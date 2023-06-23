<template>
  <v-container>
    <v-alert dismissible type="warning" v-show="errorHolder.show">{{errorHolder.message}}</v-alert>
    <v-row>
      <h3 class="mt-4">Languages/Dialects</h3>
    </v-row>
    <v-row>
      <v-col cols="12" md="6">
        <RefsetsList v-bind:refsets="refsets" @selectedRefset="captureSelection"/>
        <br/>
        <v-btn
          depressed
          color="primary"
          v-show="!this.showCreateForm"
          @click="showCreateForm=true"
        >
          Create Langauge/Dialect
        </v-btn>
      </v-col>
      <v-col md="6">
        <TranslationDetails 
          v-if="!showCreateForm && selectedRefset != null"
          v-bind:selectedRefset="selectedRefset" 
          :codeSystem="codeSystem"
          />
        <v-card class="px-4" outlined v-if="showCreateForm">
          <v-form>
            <v-container>
              <h4>Create new Language/Dialect reference set</h4>
              <v-text-field
                v-model="newLang.pt"
                :counter="200"
                label="Preferred Term"
                hint="Example: Irish language reference set"
                persistent-hint
                required
                style="margin-bottom: 30px;"
              ></v-text-field>
              <v-btn
                color="success"
                @click="createLangRefset"
              >
                Save
              </v-btn>
            </v-container>
          </v-form>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
  import axios from 'axios';
  import RefsetsList from '../refsets/RefsetsList.vue';
  import TranslationDetails from './TranslationDetails.vue';

  export default {
    name: 'TranslationManager',
    props: {
      codeSystem: {},
    },
    data: () => ({
      selectedRefset: null,
      refsets: [],
      showCreateForm: false,
      newLang: { pt: "Test language reference set" },
      errorHolder: {show: false, message: ""},
    }),
    mounted() {
      this.loadLangRefsets()
    },
    components: {
      RefsetsList,
      TranslationDetails,
    },
    methods: {
      loadLangRefsets() {
        console.log('Loading lang refsets');
        this.refsets = []
        var context = this;
        axios
          .get('api/' + this.codeSystem.shortName + '/translations')
          .then(response => {
            this.refsets = response.data;
          })
          .catch(function (error) {
            console.log(error.response.status);
            console.log(error.response.data);
            context.errorHolder.show = true
            context.errorHolder.message = "Error: " + error.response.data.message
          })

      },
      captureSelection(refset) {
        this.showCreateForm = false;
        this.selectedRefset = refset;
      },
      createLangRefset() {
        console.log('Create lang refset');
        var context = this;
        axios
          .post('api/' + this.codeSystem.shortName + '/translations', {preferredTerm: this.newLang.pt})
          .then(response => {
            response.data;
            this.showCreateForm = false;
            this.newLang.pt='';
            this.loadLangRefsets();
          })
          .catch(function (error) {
            console.log(error.response.status);
            console.log(error.response.data);
            context.errorHolder.show = true
            context.errorHolder.message = "Error: " + error.response.data.message
          })

      }
    },
  }
</script>