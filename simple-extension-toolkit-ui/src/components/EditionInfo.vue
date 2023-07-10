<template>
    <v-container>
        <v-row>
            <h3 class="mt-4">SNOMED CT Edition Details</h3>
        </v-row>
        <v-row>
          <v-col>
            <br/>
            <v-list-item-title class="text-h6 mb-1" >Module Name: <b>{{codeSystem.defaultModuleDisplay}}</b></v-list-item-title>
            <br/>
            <v-list-item-title class="text-h6 mb-1" >Module ID: <b>{{codeSystem.defaultModule}}</b></v-list-item-title>
            <br/>
            <v-list-item-title class="text-h6 mb-1" >SNOMED CT URI identifier: <b>http://snomed.info/sct/{{ codeSystem.defaultModule }}</b></v-list-item-title>
            <i><a href="http://snomed.org/uri" target="_blank">SNOMED CT URI standard</a></i>
            <br/>
            <br/>
            <v-list-item-title class="text-h6 mb-1 workingBranchPath">Working Branch: 
              <div v-if="!editBranch" style="display: inline;">
                <b>{{codeSystem.workingBranchPath}}</b> <v-icon v-on:click="editBranch=true" class="branchEdit">mdi-pencil</v-icon>
              </div>
              <div v-if="editBranch" style="display: inline;">
                <v-text-field
                    v-model="codeSystem.workingBranchPath"
                    label="Working branch for content changes"
                    style="margin-bottom: 30px;"
                  ></v-text-field>
                  <v-btn
                    color="success"
                    @click="saveWorkingBranch">Save</v-btn>
              </div>
            </v-list-item-title>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
          </v-col>
        </v-row>
    </v-container>
</template>

<script>
import axios from 'axios'
  export default {
    name: 'EditionInfo',
    props: {
      codeSystem: Object
    },
    data: () => ({
      editBranch: false,
    }),
    methods: {
      saveWorkingBranch() {
        var context = this;
        axios.post('api/codesystems/' + context.codeSystem.shortName + '/working-branch', {
          branchPath: context.codeSystem.workingBranchPath
        }).then(() => {
          axios.get('api/codesystems/' + context.codeSystem.shortName)
          .then(response => {
            context.codeSystem.workingBranchPath = response.data.workingBranchPath
            context.editBranch = false
          })
        })
      }
    }
  }
</script>
<style scoped>
  .branchEdit {
    visibility: hidden;
  }
  .workingBranchPath:hover .branchEdit {
    visibility: visible;
  }

</style>
