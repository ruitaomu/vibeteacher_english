// pages/vocabulary/vocabulary.js
const app = getApp();

Page({
  data: {
    loading: true,
    loginLoading: false,
    busy: false,
    busyText: '',
    vocabulary: [],
    displayList: [],
    error: '',
    searchText: '',
    requiresLogin: false
  },

  onLoad() {
    this.ensureLoginThenLoad();
  },

  onShow() {
    this.ensureLoginThenLoad();
  },

  ensureLoginThenLoad() {
    const isLoggedIn = app.globalData.authMode === 'wechat' && !!app.globalData.userId;
    if (isLoggedIn) {
      this.setData({ requiresLogin: false });
      this._showBusy('加载中...');
      this.loadVocabulary().finally(() => {
        this._hideBusy();
      });
      return;
    }

    this.setData({
      loading: false,
      requiresLogin: true,
      vocabulary: [],
      displayList: [],
      error: '登录后可查看词汇本。'
    });
  },

  async handleLoginTap() {
    this.setData({ loginLoading: true });
    this._showBusy('登录中...');
    try {
      await app.loginWithWechat();
      this.setData({ error: '', requiresLogin: false });
      await this.loadVocabulary();
    } catch (err) {
      this.setData({ error: err.message || '登录失败，请稍后重试。' });
    } finally {
      this.setData({ loginLoading: false });
      this._hideBusy();
    }
  },

  _showBusy(text) {
    this.setData({
      busy: true,
      busyText: text || '请稍候...'
    });
  },

  _hideBusy() {
    this.setData({
      busy: false,
      busyText: ''
    });
  },

  loadVocabulary() {
    const userId = app.globalData.userId;
    this.setData({ loading: true, error: '' });

    return new Promise((resolve, reject) => {
      wx.request({
        url: `${app.globalData.serverBase}/api/user/vocabulary/library?userId=${encodeURIComponent(userId)}`,
        method: 'GET',
        success: (res) => {
          if (res.statusCode === 200 && Array.isArray(res.data)) {
            this.setData({ vocabulary: res.data, displayList: res.data, loading: false });
          } else {
            this.setData({ vocabulary: [], displayList: [], loading: false });
          }
          resolve(res.data);
        },
        fail: (err) => {
          this.setData({ error: err.errMsg || '网络错误', loading: false });
          reject(err);
        }
      });
    });
  },

  onSearch(e) {
    const q = (e.detail.value || '').toLowerCase().trim();
    this.setData({ searchText: q });
    if (!q) {
      this.setData({ displayList: this.data.vocabulary });
    } else {
      const filtered = this.data.vocabulary.filter(
        (v) =>
          (v.word && v.word.toLowerCase().includes(q)) ||
          (v.translation && v.translation.includes(q))
      );
      this.setData({ displayList: filtered });
    }
  }
});

