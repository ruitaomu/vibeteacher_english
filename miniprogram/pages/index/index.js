// pages/index/index.js — Writing assistant page
const app = getApp();

Page({
  data: {
    loading: false,
    error: '',
    result: null,
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

      // Record the submission and check achievements
      const userId = app.globalData.userId;
      const achievementResult = await this._recordSubmission(userId);

      // Save vocabulary to user's vocabulary book
      if (revisionResult.vocabularySuggestions && revisionResult.vocabularySuggestions.length > 0) {
        await this._saveVocabulary(userId, revisionResult.vocabularySuggestions);
      }

      this.setData({
        result: revisionResult,
        newAchievements: achievementResult || []
      });

      if (achievementResult && achievementResult.length > 0) {
        wx.showToast({
          title: `🎉 获得新成就：${achievementResult[0].name}`,
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

  _requestRevise() {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${app.globalData.serverBase}/api/revise`,
        method: 'POST',
        header: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${app.globalData.sessionToken}`
        },
        data: this.data.form,
        success: (res) => {
          if (res.statusCode === 200) {
            resolve(res.data);
          } else {
            reject(new Error(res.data || `请求失败 (${res.statusCode})`));
          }
        },
        fail: (err) => reject(new Error(err.errMsg || '网络错误'))
      });
    });
  },

  _recordSubmission(userId) {
    return new Promise((resolve) => {
      wx.request({
        url: `${app.globalData.serverBase}/api/user/submission?userId=${encodeURIComponent(userId)}`,
        method: 'POST',
        success: (res) => {
          if (res.statusCode === 200 && res.data && res.data.newAchievements) {
            resolve(res.data.newAchievements);
          } else {
            resolve([]);
          }
        },
        fail: () => resolve([])
      });
    });
  },

  _saveVocabulary(userId, items) {
    return new Promise((resolve) => {
      wx.request({
        url: `${app.globalData.serverBase}/api/user/vocabulary?userId=${encodeURIComponent(userId)}`,
        method: 'POST',
        header: { 'Content-Type': 'application/json' },
        data: items,
        success: () => resolve(),
        fail: () => resolve()
      });
    });
  },

  goToAchievements() {
    wx.switchTab({ url: '/pages/achievements/achievements' });
  },

  goToVocabulary() {
    wx.switchTab({ url: '/pages/vocabulary/vocabulary' });
  }
});
