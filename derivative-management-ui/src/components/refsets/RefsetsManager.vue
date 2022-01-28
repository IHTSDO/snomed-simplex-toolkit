<template>
  <v-container>
    <v-row>
      <h3 class="mt-4">Refsets Manager - connected {{connected}}</h3>
    </v-row>
    <v-row>
      <v-col cols="12" md="6">
        <RefsetsList @selectedRefset="captureSelection" v-bind:refsets="refsets" />
        <br>
        <!-- <v-btn
          depressed
          color="primary"
        >
          Create new Refset
        </v-btn> -->
      </v-col>
      <v-col md="6">
        <RefsetDetails v-if="selectedRefset != null" 
          mode="refset"
          v-bind:selectedRefset="selectedRefset"
        />
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
  import RefsetsList from './RefsetsList.vue';
  import RefsetDetails from './RefsetDetails.vue'
  import axios from 'axios';

  export default {
    props: ['connected', 'appConfig'],
    data: () => ({
      name: 'RefsetsManager',
      refsets: [],
      selectedRefset: null
    }),
    components: {
      RefsetsList,
      RefsetDetails
    },
    mounted() {
      console.log('refset manager - mounted');
      this.loadList();
    },
    watch: {
      connected: function() {
        console.log('refset manager - connected');
        this.loadList();
      }
    },
    methods: {
      loadList() {
        console.log('refset manager - load list');
        axios
          .get('/api/refsets/simple')
          .then(response => {
            this.refsets = response.data;
          })
      },
      captureSelection(refset) {
        this.selectedRefset = refset;
        // alert(this.selectedRefset.name)
      }
    }
  }
</script>
