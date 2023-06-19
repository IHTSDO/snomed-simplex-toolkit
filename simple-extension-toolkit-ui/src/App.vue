<template>
  <v-app>
    <v-app-bar
      app
      color="primary"
      dark
    >
      <v-container>
          <h1 @click="codeSystem = null" style="cursor: pointer;">Simplex Toolkit for SNOMED CT</h1>
      </v-container>


    </v-app-bar>

    <v-main>
      <CodeSystemSelector v-if="codeSystem == null" v-bind:codesystems="codesystems" @selectedCodeSystem="captureCodeSystemSelection"/>
      <MainTabs v-if="codeSystem != null" v-bind:codeSystem="codeSystem"/>
    </v-main>
  </v-app>
</template>

<script>
import CodeSystemSelector from './components/CodeSystemSelector.vue';
import MainTabs from './components/MainTabs.vue';
import axios from 'axios';

export default {
  name: 'App',
  components: {
    CodeSystemSelector,
    MainTabs
},

  data: () => ({
    codesystems: [],
    codeSystem: null
  }),
  created () {
    document.title = "SNOMED CT Simplex Toolkit";
    axios
      .get('api/codesystems')
      .then(response => {
        this.codesystems = response.data.items;
      })
  },
  methods: {
    captureCodeSystemSelection(selectedCodeSystem) {
      this.codeSystem = selectedCodeSystem;
    }
  }
};
</script>
