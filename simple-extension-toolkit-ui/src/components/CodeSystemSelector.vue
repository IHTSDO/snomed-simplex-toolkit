<template>
  <v-container
  >
    <h2>Select a SNOMED CT Edition to work with</h2>
    <v-row>
      <v-col cols="12" md="6">
        <v-list
        style="max-height: 400px"
          class="overflow-y-auto">
          <v-list-item-group v-model="selected">
            <v-list-item two-line
              v-for="(codesystem, i) in codesystems"
              :key="i"
            >
              <v-list-item-content>
                <v-list-item-title v-text="codesystem.name"></v-list-item-title>
                <v-list-item-subtitle>{{codesystem.shortName}}</v-list-item-subtitle>
              </v-list-item-content>
            </v-list-item>
          </v-list-item-group>
        </v-list>
        <v-btn
              depressed
              color="primary"
              @click="showCreateForm = true"
              v-if="!showCreateForm"
            >
              Create or add Edition
            </v-btn>
      </v-col>
      <v-col md="6">
        <v-card class="px-4" outlined v-if="showCreateForm">
          <v-form>
            <v-container>
              <h4>Create or add SNOMED CT Edition</h4>
              <v-text-field
                v-model="newCodeSystem.name"
                :counter="200"
                label="Edition name"
                hint="Example: Angosia National Edition"
                persistent-hint
                required
                style="margin-bottom: 30px;"
              ></v-text-field>
              <v-text-field
                v-model="newCodeSystem.shortname"
                :counter="200"
                label="Short name (starting with SNOMEDCT-)"
                hint="Example: SNOMEDCT-ANG"
                persistent-hint
                required
                style="margin-bottom: 30px;"
              ></v-text-field>
              <v-text-field
                v-model="newCodeSystem.namespace"
                :counter="7"
                label="SNOMED CT Namespace"
                hint="Contact SNOMED International to register for a free namespace"
                persistent-hint
                required
                style="margin-bottom: 30px;"
              ></v-text-field>
              <v-checkbox
                v-model="newCodeSystem.createModule"
                label="Is this a new edition?"
                ></v-checkbox>
                <v-text-field
                v-model="newCodeSystem.moduleName"
                v-if="newCodeSystem.createModule"
                :counter="200"
                label="New Edition Module Name"
                hint="Example: Angosia module"
                persistent-hint
                required
                style="margin-bottom: 30px;"
              ></v-text-field>
              <v-text-field
                v-model="newCodeSystem.moduleId"
                v-if="!newCodeSystem.createModule"
                :counter="200"
                label="Edition Module Identifier"
                hint="The SNOMED CT code for your existing edition module"
                persistent-hint
                required
                style="margin-bottom: 30px;"
              ></v-text-field>
              <v-btn
                color="success"
                @click="createCodeSystem"
              >
              <span
                v-if="newCodeSystem.createModule"
                >Create edition</span>
              <span
                v-if="!newCodeSystem.createModule"
                >Add edition</span>
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

  export default {
    data: () => ({
      name: 'CodeSystemSelector',
      selected: null,
      showCreateForm: false,
      newCodeSystem: {
        name: "",
        shortname: "SNOMEDCT-",
        namespace: "",
        createModule: true,
        moduleName: "",
        moduleId: ""
      }
    }),
    props: {
      codesystems: []
    },
    watch: {
      selected: function(val) {
        this.$emit('selectedCodeSystem', this.codesystems[val])
      }
    },
    methods: {
      createCodeSystem() {
        axios
          .post('api/codesystems', {
            name: this.newCodeSystem.name, 
            shortName: this.newCodeSystem.shortname, 
            namespace: this.newCodeSystem.namespace,
            createModule: this.newCodeSystem.createModule,
            moduleName: this.newCodeSystem.moduleName,
            moduleId: this.newCodeSystem.moduleId,
          })
          .then(response => {
            response.data;
            this.showCreateForm = false;
            this.$emit('reload');
          })
      }

    }
  }
</script>
