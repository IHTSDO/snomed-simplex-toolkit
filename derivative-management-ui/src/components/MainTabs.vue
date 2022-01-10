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
      <v-tab href="#three">Translation</v-tab>
      <v-tab-item value="three">
        Tres
      </v-tab-item>
    </v-tabs>
    <v-tabs-items>
      
    </v-tabs-items>
  </v-container>
</template>

<script>
  import ConnectToTermServer from './ConnectToTermServer.vue';
  import RefsetsManager from './refsets/RefsetsManager.vue';

  export default {
    name: 'MainTabs',
    data: () => ({
    }),
    components: {
      // HelloWorld,
      ConnectToTermServer,
      RefsetsManager
    },
  }
</script>
<style scoped>
  /* h1 {
    margin-top: 100px;
  } */
</style>
