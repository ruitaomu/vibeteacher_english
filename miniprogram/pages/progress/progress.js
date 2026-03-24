// pages/progress/progress.js — 学习进度页面逻辑
const app = getApp();

Page({
  data: {
    stats: {},
    loading: false,
    error: '',
    requiresLogin: false,
    historyLoading: false,
    historyError: '',
    historyRecord: null,
    historyPage: 0,
    historyTotalPages: 0,
    historyHasNext: false,
    historyHasPrevious: false
  },

  onShow() {
    this.ensureLoginThenLoad();
  },

  ensureLoginThenLoad() {
    const isLoggedIn = app.globalData.authMode === 'wechat' && !!app.globalData.userId;
    if (isLoggedIn) {
      this.setData({ requiresLogin: false });
      this.loadStats();
      this.loadRevisionHistory(0);
      return;
    }

    this.setData({
      loading: false,
      requiresLogin: true,
      stats: {},
      error: '登录后可查看学习进度。',
      historyLoading: false,
      historyError: '',
      historyRecord: null,
      historyPage: 0,
      historyTotalPages: 0,
      historyHasNext: false,
      historyHasPrevious: false
    });
  },

  async handleLoginTap() {
    try {
      await app.loginWithWechat();
      this.setData({ error: '', requiresLogin: false });
      this.loadStats();
      this.loadRevisionHistory(0);
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
  },

  async loadRevisionHistory(page) {
    const userId = app.globalData.userId;
    if (!userId) {
      this.setData({ historyLoading: false, historyError: '用户信息加载失败' });
      return;
    }

    this.setData({ historyLoading: true, historyError: '' });

    try {
      const res = await new Promise((resolve, reject) => {
        wx.request({
          url: `${app.globalData.serverBase}/api/user/revisions?userId=${encodeURIComponent(userId)}&page=${page}&size=1`,
          method: 'GET',
          success: resolve,
          fail: reject
        });
      });

      if (res.statusCode !== 200 || !res.data) {
        this.setData({ historyError: '加载历史记录失败，请稍后再试。' });
        return;
      }

      const content = Array.isArray(res.data.content) ? res.data.content : [];
      this.setData({
        historyRecord: content.length > 0 ? content[0] : null,
        historyPage: Number.isInteger(res.data.page) ? res.data.page : page,
        historyTotalPages: Number.isInteger(res.data.totalPages) ? res.data.totalPages : 0,
        historyHasNext: !!res.data.hasNext,
        historyHasPrevious: !!res.data.hasPrevious
      });
    } catch (err) {
      this.setData({ historyError: err.message || '网络错误' });
    } finally {
      this.setData({ historyLoading: false });
    }
  },

  viewPreviousHistory() {
    if (!this.data.historyHasPrevious || this.data.historyLoading) {
      return;
    }
    this.loadRevisionHistory(this.data.historyPage - 1);
  },

  viewNextHistory() {
    if (!this.data.historyHasNext || this.data.historyLoading) {
      return;
    }
    this.loadRevisionHistory(this.data.historyPage + 1);
  }
});
