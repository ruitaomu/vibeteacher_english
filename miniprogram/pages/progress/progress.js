// pages/progress/progress.js — 学习进度页面逻辑
const app = getApp();

Page({
  data: {
    stats: {},
    loading: false,
    error: '',
    requiresLogin: false
  },

  onShow() {
    this.ensureLoginThenLoad();
  },

  ensureLoginThenLoad() {
    const isLoggedIn = app.globalData.authMode === 'wechat' && !!app.globalData.userId;
    if (isLoggedIn) {
      this.setData({ requiresLogin: false });
      this.loadStats();
      return;
    }

    this.setData({
      loading: false,
      requiresLogin: true,
      stats: {},
      error: '登录后可查看学习进度。'
    });
  },

  async handleLoginTap() {
    try {
      await app.loginWithWechat();
      this.setData({ error: '', requiresLogin: false });
      this.loadStats();
    } catch (err) {
      this.setData({ error: err.message || '登录失败，请稍后重试。' });
    }
  },

  async loadStats() {
    this.setData({ loading: true, error: '' });
    const userId = app.globalData.userId;
    if (!userId) {
      this.setData({ loading: false, error: '用户信息加载失败' });
      return;
    }
    try {
      const res = await new Promise((resolve, reject) => {
        wx.request({
          url: `${app.globalData.serverBase}/api/user/stats?userId=${encodeURIComponent(userId)}`,
          method: 'GET',
          success: resolve,
          fail: reject
        });
      });
      if (res.statusCode === 200) {
        this.setData({ stats: res.data || {} });
      } else {
        this.setData({ error: '加载进度失败，请稍后再试。' });
      }
    } catch (err) {
      this.setData({ error: err.message || '网络错误' });
    } finally {
      this.setData({ loading: false });
    }
  },

  goToAchievements() {
    wx.switchTab({ url: '/pages/achievements/achievements' });
  }
});
