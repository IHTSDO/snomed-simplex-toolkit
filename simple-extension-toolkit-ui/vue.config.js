module.exports = {
  transpileDependencies: [
    'vuetify'
  ],
  devServer: {
    port: 8085,
    proxy: {
      '^/api': {
        target: 'http://localhost:8081/',
        changeOrigin: true
      }
    }
  }
}
