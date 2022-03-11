<template>
  <v-container>
    <v-row>
      <h3 class="mt-4">Refset Manager</h3>
    </v-row>
    <v-row>
      <v-col cols="12" md="6">
        <RefsetsList @selectedRefset="captureSelection" v-bind:refsets="refsets" />
        <br>
        <v-btn
          depressed
          color="primary"
          @click="showCreateForm = true"
          v-if="!showCreateForm"
        >
          Create new Refset
        </v-btn>
      </v-col>
      <v-col md="6">
        <RefsetDetails v-if="!showCreateForm && selectedRefset != null" 
          mode="refset"
          :refsetEndpoint="refsetEndpoint"
          v-bind:selectedRefset="selectedRefset"
        />
        <v-card class="px-4" outlined v-if="showCreateForm">
          <v-form>
            <v-container>
              <h4>Create new reference set</h4>
              <v-text-field
                v-model="newRefsetTerm"
                :counter="200"
                label="Reference set name"
                hint="Example: Dentistry reference set"
                persistent-hint
                required
                style="margin-bottom: 30px;"
              ></v-text-field>
              <v-btn
                color="success"
                @click="createConcept"
              >
                Create refset concept
              </v-btn>
            </v-container>
          </v-form>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
  import RefsetsList from './RefsetsList.vue';
  import RefsetDetails from './RefsetDetails.vue'
  import axios from 'axios';

  export default {
    props: {
      connected: {
        type: Boolean
      },
      refsetEndpoint: {
        type: String
      }
    },
    data: () => ({
      name: 'RefsetsManager',
      refsets: [],
      selectedRefset: null,
      showCreateForm: false,
      newRefsetTerm: ""
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
          .get('/api/refsets/' + this.refsetEndpoint)
          .then(response => {
            this.refsets = response.data;
          })
      },
      captureSelection(refset) {
        this.showCreateForm = false;
        this.selectedRefset = refset;
      },
      createForm() {
        this.selectedRefset = null
      },
      createConcept() {
        axios
          .post('/api/refsets/' + this.refsetEndpoint, {preferredTerm: this.newRefsetTerm})
          .then(response => {
            response.data;
            this.showCreateForm = false;
            this.newRefsetTerm = null;
            this.loadList();
          })
      }
    }
  }
</script>
