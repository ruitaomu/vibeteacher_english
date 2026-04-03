// app.js — Global application logic for VibeTeacher English WeChat Mini-Program
App({
  globalData: {
    userId: '',
    // serverBase: 本地开发环境使用 127.0.0.1
    serverBase: 'https://vibeteacher.azurewebsites.net',
    sessionToken: '',
    tokenExpiresAt: 0,
    authMode: 'anonymous',
    // DevTools guard: keep anonymous flow stable even when WeChat IDE login is expired.
    devtoolsWechatLoginEnabled: true
  },

  onLaunch() {
    const token = wx.getStorageSync('sessionToken');
    const expiresAt = wx.getStorageSync('tokenExpiresAt') || 0;
    const authMode = wx.getStorageSync('authMode') || 'anonymous';
    const userId = wx.getStorageSync('userId') || '';

    if (token && Date.now() < expiresAt * 1000) {
      this.globalData.sessionToken = token;
      this.globalData.tokenExpiresAt = expiresAt;
      this.globalData.authMode = authMode;
      this.globalData.userId = authMode === 'wechat' ? userId : '';
    }
  
    // Enable share menu globally so right-top "转发" option appears when pages define onShareAppMessage
    try {
      if (wx && wx.showShareMenu) {
        wx.showShareMenu({ withShareTicket: true });
      }
    } catch (e) {
      // ignore in environments without wx
    }
  },
  // Default token fetch: anonymous mode by default.
  // WeChat login is only used for gated pages that require persisted user identity.
  getSessionToken(options = {}) {
    const forceRefresh = !!options.forceRefresh;
    if (!forceRefresh && this.isTokenValid()) {
      return Promise.resolve(this.globalData.sessionToken);
    }

    if (forceRefresh) {
      this._clearSessionState();
    }

    return this._getAnonymousSessionToken();
  },

  // Interactive login for gated pages: WeChat only, no anonymous fallback.
  loginWithWechat() {
    if (this.globalData.authMode === 'wechat' && this.globalData.userId && this.isTokenValid()) {
      return Promise.resolve(this.globalData.sessionToken);
    }
    return this._loginWithWechat();
  },

  _loginWithWechat() {
    if (this._shouldBlockWechatLoginInDevtools()) {
      return Promise.reject(new Error('当前为开发者工具环境，已禁用自动微信登录。请先用匿名模式调试；若需联调微信登录，请先在开发者工具重新登录微信并开启 devtoolsWechatLoginEnabled。'));
    }

    return new Promise((resolve, reject) => {
      wx.login({
        success: (loginRes) => {
          const code = loginRes && loginRes.code;
          if (!code) {
            reject(new Error('微信登录失败：未获取 code'));
            return;
          }

          wx.request({
            url: `${this.globalData.serverBase}/api/auth/wechat`,
            method: 'POST',
            header: { 'Content-Type': 'application/json' },
            data: { code },
            success: (res) => {
              if (res.statusCode === 200 && res.data && res.data.token && res.data.userId) {
                this.globalData.sessionToken = res.data.token;
                this.globalData.tokenExpiresAt = res.data.expiresAt;
                this.globalData.userId = res.data.userId;
                this.globalData.authMode = 'wechat';

                wx.setStorageSync('sessionToken', res.data.token);
                wx.setStorageSync('tokenExpiresAt', res.data.expiresAt);
                wx.setStorageSync('userId', res.data.userId);
                wx.setStorageSync('authMode', 'wechat');
                resolve(res.data.token);
              } else {
                const backendMsg =
                  (res && res.data && typeof res.data === 'string' && res.data.trim())
                    ? res.data.trim()
                    : '';
                reject(new Error(this._normalizeWechatLoginBackendError(res, backendMsg)));
              }
            },
            fail: (err) => reject(new Error(this._normalizeWechatLoginError(err)))
          });
        },
        fail: (err) => reject(new Error(this._normalizeWechatLoginError(err)))
      });
    });
  },

  _getAnonymousSessionToken() {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.serverBase}/api/session`,
        method: 'POST',
        success: (res) => {
          if (res.statusCode === 200 && res.data && res.data.token) {
            this.globalData.sessionToken = res.data.token;
            this.globalData.tokenExpiresAt = res.data.expiresAt;
            this.globalData.authMode = 'anonymous';
            this.globalData.userId = '';

            wx.setStorageSync('sessionToken', res.data.token);
            wx.setStorageSync('tokenExpiresAt', res.data.expiresAt);
            wx.setStorageSync('authMode', 'anonymous');
            wx.removeStorageSync('userId');
            resolve(res.data.token);
          } else {
            reject(new Error('获取令牌失败'));
          }
        },
        fail: (err) => reject(new Error(err.errMsg || '网络错误'))
      });
    });
  },

  _clearSessionState() {
    this.globalData.sessionToken = '';
    this.globalData.tokenExpiresAt = 0;
    this.globalData.authMode = 'anonymous';
    this.globalData.userId = '';

    wx.removeStorageSync('sessionToken');
    wx.removeStorageSync('tokenExpiresAt');
    wx.setStorageSync('authMode', 'anonymous');
    wx.removeStorageSync('userId');
  },

  _normalizeWechatLoginError(err) {
    const raw = (err && err.errMsg) ? String(err.errMsg) : '';
    const lower = raw.toLowerCase();
    if (lower.includes('invalid_login') || lower.includes('access_token expired')) {
      return '微信开发者工具登录态已过期。请先在开发者工具里退出并重新登录微信，再重试。';
    }
    return raw || '微信登录失败，请检查网络后重试。';
  },

  _shouldBlockWechatLoginInDevtools() {
    try {
      const info = wx.getSystemInfoSync ? wx.getSystemInfoSync() : null;
      const platform = info && info.platform ? String(info.platform).toLowerCase() : '';
      const isDevtools = platform === 'devtools';
      return isDevtools && !this.globalData.devtoolsWechatLoginEnabled;
    } catch (e) {
      return false;
    }
  },

  _normalizeWechatLoginBackendError(res, backendMsg) {
    const statusCode = res && res.statusCode;
    if (statusCode === 404) {
      return '后端未找到 /api/auth/wechat 接口。请部署最新后端版本，或将 serverBase 切到包含该接口的环境。';
    }
    if (statusCode === 401) {
      return backendMsg || '微信登录鉴权失败（code 无效或过期）。请重新登录微信后重试。';
    }
    if (statusCode === 429) {
      return backendMsg || '微信登录请求过于频繁，请稍后重试。';
    }
    if (statusCode === 500) {
      return backendMsg || '后端微信配置错误（可能是 appid/appsecret 未配置）。请联系服务端检查配置。';
    }
    if (statusCode === 502) {
      return backendMsg || '后端无法访问微信接口，请检查服务端网络连通性后重试。';
    }
    return backendMsg || `微信登录接口返回异常(${statusCode})`;
  },

  isTokenValid() {
    return (
      this.globalData.sessionToken &&
      Date.now() < this.globalData.tokenExpiresAt * 1000
    );
  }
});

