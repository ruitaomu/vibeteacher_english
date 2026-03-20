// app.js — Global application logic for VibeTeacher English WeChat Mini-Program
App({
  globalData: {
    userId: '',
    serverBase: 'https://vibeteacher.azurewebsites.net',
    sessionToken: '',
    tokenExpiresAt: 0
  },

  onLaunch() {
    // Restore or generate a persistent user ID stored in local storage
    const storedUserId = wx.getStorageSync('userId');
    if (storedUserId) {
      this.globalData.userId = storedUserId;
    } else {
      const userId = this._generateUserId();
      this.globalData.userId = userId;
      wx.setStorageSync('userId', userId);
    }

    // Restore session token if still valid
    const token = wx.getStorageSync('sessionToken');
    const expiresAt = wx.getStorageSync('tokenExpiresAt') || 0;
    if (token && Date.now() < expiresAt * 1000) {
      this.globalData.sessionToken = token;
      this.globalData.tokenExpiresAt = expiresAt;
    }
  },

  // Fetch a new session token from the backend
  getSessionToken() {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.serverBase}/api/session`,
        method: 'POST',
        success: (res) => {
          if (res.statusCode === 200 && res.data && res.data.token) {
            this.globalData.sessionToken = res.data.token;
            this.globalData.tokenExpiresAt = res.data.expiresAt;
            wx.setStorageSync('sessionToken', res.data.token);
            wx.setStorageSync('tokenExpiresAt', res.data.expiresAt);
            resolve(res.data.token);
          } else {
            reject(new Error('获取令牌失败'));
          }
        },
        fail: (err) => reject(new Error(err.errMsg || '网络错误'))
      });
    });
  },

  isTokenValid() {
    return (
      this.globalData.sessionToken &&
      Date.now() < this.globalData.tokenExpiresAt * 1000
    );
  },

  _generateUserId() {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let result = '';
    for (let i = 0; i < 32; i++) {
      result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
  }
});
