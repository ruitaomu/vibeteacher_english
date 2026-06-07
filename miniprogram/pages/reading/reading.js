const app = getApp();

Page({
  data: {
    loading: false,
    ocrLoading: false,
    error: '',
    result: null,
    ocrImageName: '',
    form: {
      model: 'gpt-5.2',
      text: ''
    },
    models: ['gpt-5.2', 'gpt-5.3'],
    modelIndex: 0
  },

  onModelChange(e) {
    const idx = parseInt(e.detail.value, 10);
    this.setData({
      modelIndex: idx,
      'form.model': this.data.models[idx]
    });
  },

  onTextInput(e) {
    this.setData({ 'form.text': e.detail.value });
  },

  chooseImageForOcr() {
    wx.showActionSheet({
      itemList: ['拍照识别', '从相册选择'],
      success: (res) => {
        const sourceType = res.tapIndex === 0 ? ['camera'] : ['album'];
        this._pickAndRecognizeImage(sourceType);
      }
    });
  },

  _pickAndRecognizeImage(sourceType) {
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType,
      success: async (res) => {
        const filePath = res.tempFilePaths && res.tempFilePaths[0];
        if (!filePath) {
          this.setData({ error: '未获取到图片，请重试。' });
          return;
        }
        const file = res.tempFiles && res.tempFiles[0];
        this.setData({
          error: '',
          ocrImageName: this._extractFileName(filePath),
          ocrLoading: true
        });

        try {
          const recognizedText = await this._uploadImageForOcr(filePath, file);
          const nextText = this._mergeRecognizedText(this.data.form.text, recognizedText);
          this.setData({
            'form.text': nextText
          });
          wx.showToast({
            title: recognizedText ? '识别完成' : '未识别到文本',
            icon: 'none'
          });
        } catch (err) {
          this.setData({ error: err.message || '图片识别失败，请稍后重试。' });
        } finally {
          this.setData({ ocrLoading: false });
        }
      },
      fail: (err) => {
        if (err && err.errMsg && !String(err.errMsg).includes('cancel')) {
          this.setData({ error: '选择图片失败，请检查相册或相机权限。' });
        }
      }
    });
  },

  _uploadImageForOcr(filePath, file) {
    return new Promise((resolve, reject) => {
      wx.uploadFile({
        url: `${app.globalData.serverBase}/api/ocr`,
        filePath,
        name: 'file',
        header: app.globalData.sessionToken
          ? { Authorization: `Bearer ${app.globalData.sessionToken}` }
          : {},
        formData: {
          fileName: this._extractFileName(filePath),
          fileType: (file && file.type) || 'image/jpeg'
        },
        success: (res) => {
          let payload = res.data;
          if (typeof payload === 'string') {
            try {
              payload = JSON.parse(payload);
            } catch (parseError) {
              if (res.statusCode === 200) {
                resolve(payload.trim());
                return;
              }
              reject(new Error(payload || `图片识别失败 (${res.statusCode})`));
              return;
            }
          }

          if (res.statusCode === 200) {
            resolve(payload && payload.text ? String(payload.text).trim() : '');
            return;
          }

          const message = typeof payload === 'string'
            ? payload
            : (payload && payload.message) || `图片识别失败 (${res.statusCode})`;
          reject(new Error(message));
        },
        fail: (err) => reject(new Error(this._normalizeUploadFailError(err)))
      });
    });
  },

  _normalizeUploadFailError(err) {
    const raw = (err && err.errMsg) ? String(err.errMsg) : '';
    const lower = raw.toLowerCase();
    if (lower.includes('url not in domain list') || lower.includes('不在以下 request 合法域名列表中')) {
      return '上传失败：当前后端域名不在小程序合法域名列表。请在微信公众平台配置 request/uploadFile 合法域名，并在开发者工具项目设置中勾选对应域名。';
    }
    return raw || '上传图片失败，请检查网络。';
  },

  _mergeRecognizedText(currentText, recognizedText) {
    const existing = (currentText || '').trim();
    const next = (recognizedText || '').trim();
    if (!next) {
      return existing;
    }
    if (!existing) {
      return next;
    }
    return `${existing}\n${next}`;
  },

  _extractFileName(filePath) {
    if (!filePath) {
      return '';
    }
    const parts = String(filePath).split('/');
    return parts[parts.length - 1] || 'image.jpg';
  },

  async submit() {
    this.setData({ error: '', result: null });

    const text = this.data.form.text.trim();
    if (!text) {
      this.setData({ error: '请输入英文内容。' });
      return;
    }

    this.setData({ loading: true });

    try {
      if (!app.isTokenValid()) {
        await app.getSessionToken();
      }

      const readingResult = await this._requestReadingAnalysis();
      this.setData({
        result: this._decorateReadingResult(readingResult)
      });
    } catch (err) {
      this.setData({ error: err.message || '请求失败，请重试。' });
    } finally {
      this.setData({ loading: false });
    }
  },

  _decorateReadingResult(readingResult) {
    const sentences = Array.isArray(readingResult && readingResult.sentences)
      ? readingResult.sentences.map((sentence) => ({
          original: sentence && sentence.original ? sentence.original : '',
          translation: sentence && sentence.translation ? sentence.translation : '',
          keyWords: this._decorateLearningPoints(sentence && sentence.keyWords),
          keyPhrases: this._decorateLearningPoints(sentence && sentence.keyPhrases),
          typicalPatterns: this._decorateLearningPoints(sentence && sentence.typicalPatterns),
          structureExplanation: '',
          structureLoading: false,
          structureError: ''
        }))
      : [];

    return {
      overallTranslation: readingResult && readingResult.overallTranslation
        ? readingResult.overallTranslation
        : '',
      sentences
    };
  },

  _decorateLearningPoints(items) {
    if (!Array.isArray(items)) {
      return [];
    }
    return items
      .filter((item) => item && item.term)
      .map((item) => ({
        term: item.term,
        explanation: item.explanation || '',
        exampleSentence: item.exampleSentence || '',
        saved: false,
        saving: false
      }));
  },

  _requestReadingAnalysis(isRetry) {
    return new Promise((resolve, reject) => {
      const payload = {
        model: this.data.form.model,
        text: this.data.form.text
      };

      wx.request({
        url: `${app.globalData.serverBase}/api/reading/analyze`,
        method: 'POST',
        header: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${app.globalData.sessionToken}`
        },
        data: payload,
        success: (res) => {
          if (res.statusCode === 200) {
            resolve(res.data);
          } else if (res.statusCode === 401 && !isRetry) {
            app.getSessionToken({ forceRefresh: true })
              .then(() => this._requestReadingAnalysis(true))
              .then(resolve)
              .catch(reject);
          } else {
            const message = this._extractApiErrorMessage(res) || `请求失败 (${res.statusCode})`;
            reject(new Error(message));
          }
        },
        fail: (err) => reject(new Error(err.errMsg || '网络错误'))
      });
    });
  },

  handleStructureExplain(e) {
    const sentenceIndex = Number(e.currentTarget.dataset.sentenceIndex);
    const sentence = this._getSentenceByIndex(sentenceIndex);
    if (!sentence || !sentence.original || sentence.structureLoading) {
      return;
    }

    this.setData({
      [`result.sentences[${sentenceIndex}].structureLoading`]: true,
      [`result.sentences[${sentenceIndex}].structureError`]: ''
    });

    this._requestSentenceStructure(sentence.original)
      .then((response) => {
        this.setData({
          [`result.sentences[${sentenceIndex}].structureExplanation`]: response && response.structureExplanation
            ? response.structureExplanation
            : '未返回句子结构讲解。',
          [`result.sentences[${sentenceIndex}].structureLoading`]: false
        });
      })
      .catch((err) => {
        this.setData({
          [`result.sentences[${sentenceIndex}].structureLoading`]: false,
          [`result.sentences[${sentenceIndex}].structureError`]: err.message || '句子结构讲解失败，请稍后重试。'
        });
      });
  },

  _requestSentenceStructure(sentence, isRetry) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${app.globalData.serverBase}/api/reading/structure`,
        method: 'POST',
        header: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${app.globalData.sessionToken}`
        },
        data: {
          model: this.data.form.model,
          sentence
        },
        success: (res) => {
          if (res.statusCode === 200) {
            resolve(res.data);
          } else if (res.statusCode === 401 && !isRetry) {
            app.getSessionToken({ forceRefresh: true })
              .then(() => this._requestSentenceStructure(sentence, true))
              .then(resolve)
              .catch(reject);
          } else {
            const message = this._extractApiErrorMessage(res) || `请求失败 (${res.statusCode})`;
            reject(new Error(message));
          }
        },
        fail: (err) => reject(new Error(err.errMsg || '网络错误'))
      });
    });
  },

  async handleAddLearningPoint(e) {
    const sentenceIndex = Number(e.currentTarget.dataset.sentenceIndex);
    const itemIndex = Number(e.currentTarget.dataset.itemIndex);
    const category = e.currentTarget.dataset.category;
    const sentence = this._getSentenceByIndex(sentenceIndex);
    if (!sentence) {
      return;
    }

    const item = Array.isArray(sentence[category]) ? sentence[category][itemIndex] : null;
    if (!item || item.saved || item.saving) {
      return;
    }

    const itemPath = `result.sentences[${sentenceIndex}].${category}[${itemIndex}]`;
    this.setData({
      [`${itemPath}.saving`]: true
    });

    try {
      await this._ensureVocabularyLogin();
      await this._addVocabularyItem(this._toVocabularyItem(category, item, sentence.original));
      this.setData({
        [`${itemPath}.saving`]: false,
        [`${itemPath}.saved`]: true
      });
      wx.showToast({
        title: '已加入词汇本',
        icon: 'success'
      });
    } catch (err) {
      this.setData({
        [`${itemPath}.saving`]: false,
        error: err.message || '加入词汇本失败，请稍后重试。'
      });
    }
  },

  _ensureVocabularyLogin() {
    const isLoggedIn = app.globalData.authMode === 'wechat' && !!app.globalData.userId;
    if (isLoggedIn) {
      return Promise.resolve();
    }

    wx.showLoading({ title: '登录中...' });
    return app.loginWithWechat()
      .finally(() => {
        wx.hideLoading();
      });
  },

  _addVocabularyItem(item) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${app.globalData.serverBase}/api/user/vocabulary?userId=${encodeURIComponent(app.globalData.userId)}`,
        method: 'POST',
        header: {
          'Content-Type': 'application/json'
        },
        data: [item],
        success: (res) => {
          if (res.statusCode === 200) {
            resolve(res.data);
            return;
          }
          const message = this._extractApiErrorMessage(res) || `请求失败 (${res.statusCode})`;
          reject(new Error(message));
        },
        fail: (err) => reject(new Error(err.errMsg || '网络错误'))
      });
    });
  },

  _toVocabularyItem(category, item, sentenceOriginal) {
    const exampleSentence = item.exampleSentence || sentenceOriginal || '';
    if (category === 'typicalPatterns') {
      return {
        word: item.term,
        translation: `典型句式：${item.explanation || ''}`,
        exampleSentence
      };
    }

    return {
      word: item.term,
      translation: item.explanation || '',
      exampleSentence
    };
  },

  _getSentenceByIndex(sentenceIndex) {
    const result = this.data.result;
    if (!result || !Array.isArray(result.sentences)) {
      return null;
    }
    return result.sentences[sentenceIndex] || null;
  },

  _extractApiErrorMessage(res) {
    if (!res) {
      return '';
    }
    const data = res.data;
    if (typeof data === 'string') {
      return data.trim();
    }
    if (data && typeof data === 'object') {
      if (typeof data.message === 'string' && data.message.trim()) {
        return data.message.trim();
      }
      if (typeof data.error === 'string' && data.error.trim()) {
        return data.error.trim();
      }
      if (typeof data.path === 'string' && typeof data.status === 'number') {
        return `请求失败 (${data.status}) ${data.path}`;
      }
    }
    return '';
  },

  onShareAppMessage() {
    return {
      title: 'VibeTeacher 阅读助手',
      path: '/pages/reading/reading'
    };
  }
});
