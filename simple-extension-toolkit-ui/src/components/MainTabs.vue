<template>
  <v-container>
    <ConnectToTermServer @connected="connect" @updateAppConfig="updateAppConfig"/>
    <v-tabs>
      <v-tab href="#simple">
        <v-icon>mdi-playlist-star</v-icon>
        Simple Refsets
      </v-tab>
      <v-tab-item value="simple" :disabled="!connected">
        <RefsetsManager v-bind:connected="connected" 
          :refsetEndpoint="'simple'"
          :title="''"/>
      </v-tab-item>
      <v-tab href="#maps" :disabled="!connected">
        <v-icon>mdi-transfer-right</v-icon>
        Map Refsets
      </v-tab>
      <v-tab-item value="maps">
        <RefsetsManager v-bind:connected="connected" 
          :refsetEndpoint="'simple-map-to-snomed-with-correlation'"
          :title="'Simple maps with correlation to SNOMED CT'"/>
      </v-tab-item>
      <v-tab href="#translations" :disabled="!connected">
        <v-icon>mdi-earth</v-icon>
        Translations
      </v-tab>
      <v-tab-item value="translations">
        <TranslationManager :connectionDetails="connectionDetails"/>
      </v-tab-item>
      <v-tab href="#release" :disabled="!connected">
        <v-icon>mdi-rocket</v-icon>
        Release &amp; Export
      </v-tab>
      <v-tab-item value="release">
        <ExportManager/>
      </v-tab-item>
    </v-tabs>
  </v-container>
</template>

<script>
  import ConnectToTermServer from './ConnectToTermServer.vue';
  import RefsetsManager from './refsets/RefsetsManager.vue';
  // import TranslationManager from './translation/TranslationManager.vue';
  import ExportManager from './export/ExportManager.vue'
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
      ExportManager,
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
  .v-tab .v-icon {
    margin-right: 10px;
  }
</style>
