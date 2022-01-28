<template>
  <v-container>
    <v-tabs>
      <v-tab href="#one">Setup</v-tab>
      <v-tab-item value="one">
        <ConnectToTermServer @connected="connect" @updateAppConfig="updateAppConfig"/>
      </v-tab-item>
      <v-tab href="#two" :disabled="!connected">
          Simple Type Refsets
        </v-tab>
      <v-tab-item value="two">
        <RefsetsManager v-bind:connected="connected"/>
      </v-tab-item>
      <!-- <v-tab href="#five" :disabled="!connected">
        Maps
      </v-tab>
      <v-tab-item value="five">
        <MapsManager/>
      </v-tab-item>
      <v-tab href="#three" :disabled="!connected">
        Translation
      </v-tab>
      <v-tab-item value="three">
        <TranslationManager :connectionDetails="connectionDetails"/>
      </v-tab-item>
      <v-tab href="#four" :disabled="!connected">
        Export
      </v-tab> -->
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
  // import TranslationManager from './translation/TranslationManager.vue';
  // import ExportManager from './export/ExportManager.vue'
  // import MapsManager from './maps/MapsManager.vue'


  export default {
    name: 'MainTabs',
    data: () => ({
      connected: false,
      connectionDetails: {},
      topAppConfig: {}
    }),
    components: {
      // HelloWorld,
      ConnectToTermServer,
      RefsetsManager,
      // TranslationManager,
      // ExportManager,
      // MapsManager
    },
    methods: {
      connect(val) {
        console.log('event handler - connect');
        this.connected = val;
      },
      updateAppConfig(val) {
        console.log('event handler - updateAppConfig', val);
        this.topAppConfig = val;
      }
    }
  }
</script>
<style scoped>
  /* h1 {
    margin-top: 100px;
  } */
</style>
