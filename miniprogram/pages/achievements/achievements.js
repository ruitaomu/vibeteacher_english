// pages/achievements/achievements.js
const app = getApp();

Page({
  data: {
    loading: true,
    achievements: [],
    stats: null,
    error: ''
  },

  onLoad() {
    this.loadData();
  },

  onShow() {
    this.loadData();
  },

  loadData() {
    const userId = app.globalData.userId;
    this.setData({ loading: true, error: '' });

    Promise.all([
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
