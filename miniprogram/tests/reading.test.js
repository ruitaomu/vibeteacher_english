const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'pages', 'reading', 'reading.js'), 'utf8');

function createPage(request) {
  const app = {
    globalData: { serverBase: 'http://127.0.0.1:8080', sessionToken: 'test-session' },
    isTokenValid: () => true,
    getSessionToken: async () => {}
  };
  let page;
  vm.runInNewContext(source, {
    getApp: () => app,
    wx: { request },
    Page: (definition) => { page = definition; }
  });
  page.data = JSON.parse(JSON.stringify(page.data));
  page.setData = function (updates) {
    for (const [key, value] of Object.entries(updates)) {
      if (key === 'form.text') {
        this.data.form.text = value;
      } else {
        this.data[key] = value;
      }
    }
  };
  page._showBusy = () => {};
  page._hideBusy = () => {};
  return { page, app };
}

function parseQuery(url) {
  const query = url.split('?')[1] || '';
  const params = { level: '', excludeUrls: [] };
  for (const pair of query.split('&').filter(Boolean)) {
    const [key, value] = pair.split('=');
    if (key === 'level') {
      params.level = decodeURIComponent(value);
    } else if (key === 'excludeUrl') {
      params.excludeUrls.push(decodeURIComponent(value));
    }
  }
  return params;
}

test('initial article request has a level and no excluded article', async () => {
  let request;
  const { page } = createPage((options) => {
    request = options;
    options.success({ statusCode: 200, data: { url: 'https://example.org/first', text: 'First' } });
  });

  await page.fetchArticle();

  assert.match(request.url, /^http:\/\/127\.0\.0\.1:8080\/api\/reading\/article\?/);
  assert.equal(request.method, 'GET');
  assert.deepEqual(parseQuery(request.url), { level: '中学12年级', excludeUrls: [] });
  assert.equal(page.data.form.text, 'First');
  assert.deepEqual([...page.data.seenArticleUrls], ['https://example.org/first']);
  assert.equal(page.data.articleLoading, false);
});

test('changing article excludes every recently seen URL, current first', async () => {
  const requests = [];
  let next = 0;
  const { page } = createPage((options) => {
    requests.push(parseQuery(options.url));
    next += 1;
    options.success({ statusCode: 200, data: { url: `https://example.org/a,${next}?x=1&y=2`, text: `Text ${next}` } });
  });

  await page.fetchArticle();
  await page.fetchArticle();
  await page.fetchArticle();

  assert.deepEqual(requests[0].excludeUrls, []);
  assert.deepEqual(requests[1].excludeUrls, ['https://example.org/a,1?x=1&y=2']);
  assert.deepEqual(requests[2].excludeUrls, ['https://example.org/a,2?x=1&y=2', 'https://example.org/a,1?x=1&y=2']);
  assert.equal(page.data.article.url, 'https://example.org/a,3?x=1&y=2');
  assert.equal(page.data.form.text, 'Text 3');
});

test('changing article clears the previous analysis', async () => {
  const { page } = createPage((options) => {
    options.success({ statusCode: 200, data: { url: 'https://example.org/next', text: 'Next' } });
  });
  page.data.article = { url: 'https://example.org/current', text: 'Current' };
  page.data.seenArticleUrls = ['https://example.org/current'];
  page.data.result = { overallTranslation: 'Previous analysis' };

  await page.fetchArticle();

  assert.equal(page.data.result, null);
  assert.deepEqual([...page.data.seenArticleUrls], ['https://example.org/next', 'https://example.org/current']);
});

test('seen history is capped so the request URL stays bounded', async () => {
  let count = 0;
  const { page } = createPage((options) => {
    count += 1;
    options.success({ statusCode: 200, data: { url: `https://example.org/${count}`, text: 'Text' } });
  });

  for (let i = 0; i < 40; i++) {
    await page.fetchArticle();
  }

  assert.equal(page.data.seenArticleUrls.length, 30);
  assert.equal(page.data.seenArticleUrls[0], 'https://example.org/40');
});

test('token retry retains the requested level and excluded URLs', async () => {
  const requests = [];
  const { page, app } = createPage((options) => {
    requests.push(options);
    options.success(requests.length === 1
      ? { statusCode: 401 }
      : { statusCode: 200, data: { url: 'https://example.org/next' } });
  });
  page.data.article = { url: 'https://example.org/current' };
  page.data.seenArticleUrls = ['https://example.org/current', 'https://example.org/older'];
  app.getSessionToken = async () => {
    page.data.levelIndex = 0;
    page.data.seenArticleUrls = ['https://example.org/changed'];
  };

  await page._requestArticle();

  assert.equal(requests.length, 2);
  assert.deepEqual(parseQuery(requests[1].url), {
    level: '中学12年级',
    excludeUrls: ['https://example.org/current', 'https://example.org/older']
  });
});

test('unavailable alternative preserves the current article and analysis', async () => {
  const message = 'No other article is available';
  const { page } = createPage((options) => {
    options.success({ statusCode: 502, data: message });
  });
  const article = { url: 'https://example.org/current', text: 'Current' };
  const result = { overallTranslation: 'Previous analysis' };
  page.data.article = article;
  page.data.form.text = 'Current';
  page.data.result = result;

  await page.fetchArticle();

  assert.equal(page.data.article, article);
  assert.equal(page.data.form.text, 'Current');
  assert.equal(page.data.result, result);
  assert.equal(page.data.error, message);
  assert.equal(page.data.articleLoading, false);
});

test('a duplicate response is reported rather than shown as a successful change', async () => {
  const article = { url: 'https://example.org/current', text: 'Current' };
  const { page } = createPage((options) => {
    options.success({ statusCode: 200, data: { ...article } });
  });
  const result = { overallTranslation: 'Previous analysis' };
  page.data.article = article;
  page.data.form.text = 'Current';
  page.data.result = result;

  await page.fetchArticle();

  assert.equal(page.data.article, article);
  assert.equal(page.data.result, result);
  assert.match(page.data.error, /暂未获取到不同的文章/);
  assert.equal(page.data.articleLoading, false);
});
