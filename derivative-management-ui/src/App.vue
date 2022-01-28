<template>
  <v-app>
    <v-app-bar
      app
      color="primary"
      dark
    >
      <div class="d-flex align-center">
        <!-- <v-img
          alt="Vuetify Logo"
          class="shrink mr-2"
          contain
          src="https://cdn.vuetifyjs.com/images/logos/vuetify-logo-dark.png"
          transition="scale-transition"
          width="40"
        /> -->
        <h1>SNOMED CT Simple Extension Toolkit</h1>
      </div>

      <v-spacer></v-spacer>

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
};
</script>
