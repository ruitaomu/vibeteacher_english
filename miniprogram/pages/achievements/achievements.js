// pages/achievements/achievements.js
const app = getApp();

Page({
  data: {
    loading: true,
    loginLoading: false,
    busy: false,
    busyText: '',
    achievements: [],
    stats: null,
    error: '',
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
      this.loadData().finally(() => {
        this._hideBusy();
      });
      return;
    }

    this.setData({
      loading: false,
      requiresLogin: true,
      achievements: [],
      stats: null,
      error: '登录后可查看我的成就。'
    });
  },

  async handleLoginTap() {
    this.setData({ loginLoading: true });
    this._showBusy('登录中...');
    try {
      await app.loginWithWechat();
      this.setData({ error: '', requiresLogin: false });
      await this.loadData();
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

  loadData() {
    const userId = app.globalData.userId;
    this.setData({ loading: true, error: '' });

    return Promise.all([
      this._fetchAchievements(userId),
      this._fetchStats(userId)
    ]).then(([achievements, stats]) => {
      this.setData({ achievements, stats, loading: false });
    }).catch((err) => {
      this.setData({ error: err.message || '加载失败', loading: false });
    });
  },

  _fetchAchievements(userId) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${app.globalData.serverBase}/api/user/achievements?userId=${encodeURIComponent(userId)}`,
        method: 'GET',
        success: (res) => {
          if (res.statusCode === 200 && Array.isArray(res.data)) {
            resolve(res.data);
          } else {
            resolve([]);
          }
        },
        fail: (err) => reject(new Error(err.errMsg || '网络错误'))
      });
    });
  },

  _fetchStats(userId) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${app.globalData.serverBase}/api/user/stats?userId=${encodeURIComponent(userId)}`,
        method: 'GET',
        success: (res) => {
          if (res.statusCode === 200) {
            resolve(res.data);
          } else {
            resolve(null);
          }
        },
        fail: (err) => reject(new Error(err.errMsg || '网络错误'))
      });
    });
  }
});
