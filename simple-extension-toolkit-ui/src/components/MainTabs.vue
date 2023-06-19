<template>
  <v-container>
    <h2>{{codeSystem.name}}</h2>
    <v-tabs>
      <v-tab href="#info">
        <v-icon>mdi-information</v-icon>
        Info
      </v-tab>
      <v-tab-item value="info">
        <EditionInfo
        :codeSystem="codeSystem"/>
      </v-tab-item>
      <v-tab href="#simple">
        <v-icon>mdi-playlist-star</v-icon>
        Subsets
      </v-tab>
      <v-tab-item value="simple">
        <RefsetsManager
          :codeSystem="codeSystem"
          :refsetEndpoint="'simple'"
          :title="''"/>
      </v-tab-item>
      <v-tab href="#maps" >
        <v-icon>mdi-transfer-right</v-icon>
        Maps
      </v-tab>
      <v-tab-item value="maps">
        <RefsetsManager
          :codeSystem="codeSystem"
          :refsetEndpoint="'simple-map-to-snomed-with-correlation'"
          :title="'Simple maps with correlation to SNOMED CT'"/>
      </v-tab-item>
      <v-tab href="#translations">
        <v-icon>mdi-earth</v-icon>
        Translations
      </v-tab>
      <v-tab-item value="translations">
        <TranslationManager
        :codeSystem="codeSystem"/>
      </v-tab-item>
      <v-tab href="#release">
        <v-icon>mdi-rocket</v-icon>
        Release &amp; Export
      </v-tab>
      <v-tab-item value="release">
        <ExportManager
          :codeSystem="codeSystem"/>
      </v-tab-item>
    </v-tabs>
  </v-container>
</template>

<script>
  import RefsetsManager from './refsets/RefsetsManager.vue';
  // import TranslationManager from './translation/TranslationManager.vue';
  import ExportManager from './export/ExportManager.vue'
  import EditionInfo from './EditionInfo.vue'
  // import MapsManager from './maps/MapsManager.vue'

  import axios from 'axios';

  export default {
    name: 'MainTabs',
    data: () => ({
      connected: false,
      connectionDetails: {},
      topAppConfig: {}
    }),
    props: {
      codeSystem: null
    },
    components: {
      RefsetsManager,
      // TranslationManager,
      ExportManager,
      EditionInfo,
      // MapsManager
    },
    mounted() {
      var context = this;
      axios
        .get('api/codesystems/' + this.codeSystem.shortName)
        .then(function(response) {
          var codeSystemWithDetails = response.data;
          context.$set(context.codeSystem, 'defaultModule', codeSystemWithDetails.defaultModule)
          context.$set(context.codeSystem, 'defaultModuleDisplay', codeSystemWithDetails.defaultModuleDisplay)
        })
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
