<template>
  <v-container>
    <v-tabs>
      <v-tab href="#one">Snowstorm</v-tab>
      <v-tab-item value="one">
        <ConnectToTermServer/>
      </v-tab-item>
      <v-tab href="#two">Simple Type Refsets</v-tab>
      <v-tab-item value="two">
        <RefsetsManager/>
      </v-tab-item>
      <v-tab href="#five">Maps</v-tab>
      <v-tab-item value="five">
        <MapsManager/>
      </v-tab-item>
      <v-tab href="#three">Translation</v-tab>
      <v-tab-item value="three">
        <TranslationManager/>
      </v-tab-item>
      <v-tab href="#four">Export</v-tab>
      <v-tab-item value="four">
        <ExportManager/>
      </v-tab-item>
    </v-tabs>
    <v-tabs-items>
      
    </v-tabs-items>
  </v-container>
</template>

<script>
  import ConnectToTermServer from './ConnectToTermServer.vue';
  import RefsetsManager from './refsets/RefsetsManager.vue';
  import TranslationManager from './translation/TranslationManager.vue';
  import ExportManager from './export/ExportManager.vue'
  import MapsManager from './maps/MapsManager.vue'


  export default {
    name: 'MainTabs',
    data: () => ({
    }),
    components: {
      // HelloWorld,
      ConnectToTermServer,
      RefsetsManager,
      TranslationManager,
      ExportManager,
      MapsManager
    },
  }
</script>
<style scoped>
  /* h1 {
    margin-top: 100px;
  } */
</style>
