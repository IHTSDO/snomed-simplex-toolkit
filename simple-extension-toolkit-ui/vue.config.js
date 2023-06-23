module.exports = {
  transpileDependencies: [
    'vuetify'
  ],
  publicPath: '/simplex-toolkit',
  devServer: {
    port: 8085,
    public: 'https://localhost.ihtsdotools.org/simplex-toolkit',
    disableHostCheck : true,
    // proxy: {
    //   '^/api': {
    //     target: 'http://localhost:8081/',
    //     changeOrigin: true
    //   }
    // }
  }
}
