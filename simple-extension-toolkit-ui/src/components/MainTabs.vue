<template>
  <v-container>
    <v-tabs>
      <v-tab href="#setup">Setup</v-tab>
      <v-tab-item value="setup">
        <ConnectToTermServer @connected="connect" @updateAppConfig="updateAppConfig"/>
      </v-tab-item>
      <v-tab href="#simple" :disabled="!connected">
          Simple Refsets
        </v-tab>
      <v-tab-item value="simple">
        <RefsetsManager v-bind:connected="connected" :refsetEndpoint="'simple'"/>
      </v-tab-item>
      <v-tab href="#simple-map-to-snomed-with-correlation" :disabled="!connected">
        Simple Map to SNOMED with Correlation
      </v-tab>
      <v-tab-item value="simple-map-to-snomed-with-correlation">
        <RefsetsManager v-bind:connected="connected" :refsetEndpoint="'simple-map-to-snomed-with-correlation'"/>
      </v-tab-item>
      <v-tab href="#translation" :disabled="!connected">
        Translation
      </v-tab>
      <v-tab-item value="translation">
        <TranslationManager :connectionDetails="connectionDetails"/>
      </v-tab-item>
      <v-tab href="#release" :disabled="!connected">
        Release &amp; Export
      </v-tab>
      <v-tab-item value="release">
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
