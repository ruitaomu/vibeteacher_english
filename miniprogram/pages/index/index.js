// pages/index/index.js — Writing assistant page
const app = getApp();

Page({
  data: {
    loading: false,
    ocrLoading: false,
    error: '',
    result: null,
    ocrImageName: '',
    form: {
      level: '中学12年级',
      model: 'gpt-5.2',
      text: '',
      requirements: '',
      expand: false
    },
    levels: [
      '中学7年级', '中学8年级', '中学9年级', '中学10年级',
      '中学11年级', '中学12年级',
      '大学英语四级', '大学英语六级', 'TOEFL写作', 'GRE写作'
    ],
    levelIndex: 5,
    models: ['gpt-5.2', 'gpt-5.3'],
    modelIndex: 0,
    newAchievements: []
  },

  onLoad() {},

  onLevelChange(e) {
    const idx = parseInt(e.detail.value);
    this.setData({
      levelIndex: idx,
      'form.level': this.data.levels[idx]
    });
  },

  onModelChange(e) {
    const idx = parseInt(e.detail.value);
    this.setData({
      modelIndex: idx,
      'form.model': this.data.models[idx]
    });
  },

  onTextInput(e) {
    this.setData({ 'form.text': e.detail.value });
  },

  onRequirementsInput(e) {
    this.setData({ 'form.requirements': e.detail.value });
  },

  onExpandChange(e) {
    this.setData({ 'form.expand': e.detail.value });
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
    this.setData({ error: '', result: null, newAchievements: [] });

    const text = this.data.form.text.trim();
    if (!text) {
      this.setData({ error: '请输入英文片段。' });
      return;
    }

    this.setData({ loading: true });

    try {
      // Ensure we have a valid session token
      if (!app.isTokenValid()) {
        await app.getSessionToken();
      }

      const revisionResult = await this._requestRevise();
      const newAchievements = Array.isArray(revisionResult.newAchievements)
        ? revisionResult.newAchievements
        : [];

      this.setData({
        result: revisionResult,
        newAchievements
      });

      if (newAchievements.length > 0) {
        wx.showToast({
          title: `🎉 获得新成就：${newAchievements[0].name}`,
          icon: 'none',
          duration: 3000
        });
      }
    } catch (err) {
      this.setData({ error: err.message || '请求失败，请重试。' });
    } finally {
      this.setData({ loading: false });
    }
  },

  _requestRevise(isRetry) {
    return new Promise((resolve, reject) => {
      const isLoggedIn = app.globalData.authMode === 'wechat' && !!app.globalData.userId;
      const payload = {
        ...this.data.form
      };
      if (isLoggedIn) {
        payload.userId = app.globalData.userId;
      }

      wx.request({
        url: `${app.globalData.serverBase}/api/revise`,
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
              .then(() => this._requestRevise(true))
              .then(resolve)
              .catch(reject);
          } else {
            const message = this._extractApiErrorMessage(res)
              || `请求失败 (${res.statusCode})`;
            reject(new Error(message));
          }
        },
        fail: (err) => reject(new Error(err.errMsg || '网络错误'))
      });
    });
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

  goToAchievements() {
    wx.switchTab({ url: '/pages/achievements/achievements' });
  },

  goToVocabulary() {
    wx.switchTab({ url: '/pages/vocabulary/vocabulary' });
  },

  onShareAppMessage() {
    return {
      title: 'VibeTeacher 英语写作助手',
      path: '/pages/index/index'
    };
  }
});
