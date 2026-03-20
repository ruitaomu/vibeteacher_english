// pages/vocabulary/vocabulary.js
const app = getApp();

Page({
  data: {
    loading: true,
    vocabulary: [],
    displayList: [],
    error: '',
    searchText: ''
  },

  onLoad() {
    this.loadVocabulary();
  },

  onShow() {
    this.loadVocabulary();
  },

  loadVocabulary() {
    const userId = app.globalData.userId;
    this.setData({ loading: true, error: '' });

    wx.request({
      url: `${app.globalData.serverBase}/api/user/vocabulary?userId=${encodeURIComponent(userId)}`,
      method: 'GET',
      success: (res) => {
        if (res.statusCode === 200 && Array.isArray(res.data)) {
          this.setData({ vocabulary: res.data, displayList: res.data, loading: false });
        } else {
          this.setData({ vocabulary: [], displayList: [], loading: false });
        }
      },
      fail: (err) => {
        this.setData({ error: err.errMsg || '网络错误', loading: false });
      }
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

