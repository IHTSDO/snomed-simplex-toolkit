<template>
  <v-container>
    <v-tabs>
      <v-tab href="#one">Snowstorm</v-tab>
      <v-tab-item value="one">
        <ConnectToTermServer @connected="connect" @connectionDetails="updateConnectionDetails"/>
      </v-tab-item>
      <v-tab href="#two" :disabled="!connected">
          Simple Type Refsets
        </v-tab>
      <v-tab-item value="two">
        <RefsetsManager/>
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
      connectionDetails: {}
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
        this.connected = val;
      },
      updateConnectionDetails(val) {
        this.connectionDetails = val;
      }
    }
  }
</script>
<style scoped>
  /* h1 {
    margin-top: 100px;
  } */
</style>
