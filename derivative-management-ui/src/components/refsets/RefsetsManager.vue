<template>
  <v-container>
    <v-row>
      <h3 class="mt-4">Refsets Manager</h3>
    </v-row>
    <v-row>
      <v-col cols="12" md="6">
        <RefsetsList @selectedRefset="captureSelection" />
        <br>
        <v-btn
          depressed
          color="primary"
        >
          Create new Refset
        </v-btn>
      </v-col>
      <v-col md="6">
        <RefsetDetails v-if="selectedRefset != null" 
        :refsetName= "selectedRefset.name" 
        :refsetSubt= "selectedRefset.subtitle"
        />
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
  import RefsetsList from './RefsetsList.vue';
  import RefsetDetails from './RefsetDetails.vue'

  export default {
    data: () => ({
      name: 'RefsetsManager',
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