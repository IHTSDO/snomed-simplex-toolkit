<template>
  <v-container>
    <v-row>
      <h3 class="mt-4">Maps Manager</h3>
    </v-row>
    <v-row>
      <v-col cols="12" md="6">
        <RefsetsList @selectedRefset="captureSelection" mode="maps"/>
        <br>
        <v-btn
          depressed
          color="primary"
        >
          Create new Map
        </v-btn>
      </v-col>
      <v-col md="6">
        <RefsetDetails v-if="selectedRefset != null" 
        :refsetName= "selectedRefset.name" 
        :refsetSubt= "selectedRefset.subtitle"
        mode="map"
        />
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
  import RefsetsList from '../refsets/RefsetsList.vue';
  import RefsetDetails from '../refsets/RefsetDetails.vue'

  export default {
    data: () => ({
      name: 'MapsManager',
      selectedRefset: null,
    }),
    components: {
      RefsetsList,
      RefsetDetails
    },
    methods: {
      captureSelection(refset) {
        this.selectedRefset = refset;
        // alert(this.selectedRefset.name)
      }
    }
  }
</script>