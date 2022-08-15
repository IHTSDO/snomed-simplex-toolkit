module.exports = {
  transpileDependencies: [
    'vuetify'
  ],
  publicPath: '/simplex-toolkit/',
  devServer: {
    port: 8085,
    public: 'https://localhost',
    disableHostCheck : true,
    proxy: {
      '^/api': {
        target: 'http://localhost:8081/',
        changeOrigin: true
      }
    }
  }
}
