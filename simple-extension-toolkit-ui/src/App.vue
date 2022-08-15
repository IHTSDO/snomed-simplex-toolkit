<template>
  <v-app>
    <v-app-bar
      app
      color="primary"
      dark
    >
      <v-container>
          <h1>SNOMED CT Simplex Toolkit</h1>
      </v-container>


    </v-app-bar>

    <v-main>
      <MainTabs/>
    </v-main>
  </v-app>
</template>

<script>
import MainTabs from './components/MainTabs.vue';

export default {
  name: 'App',
  components: {
    // HelloWorld,
    MainTabs,
  },

  data: () => ({
    //
  }),
  created () {
    document.title = "SNOMED CT Simplex Toolkit";
  }
};
</script>
