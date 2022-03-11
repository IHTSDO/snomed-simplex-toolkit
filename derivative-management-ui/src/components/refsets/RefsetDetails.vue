<template>
  <v-card
    class="px-4"
    outlined
  >
    <v-list-item-content>
        <div class="text-overline mb-4" v-text="mode"></div>
        <v-list-item-title class="text-h5 mb-1" >{{selectedRefset.pt.term}}<br/>ConceptId: {{selectedRefset.conceptId}}</v-list-item-title>
        <v-list-item-subtitle>{{selectedRefset.activeMemberCount}} active members.</v-list-item-subtitle>
    </v-list-item-content>
    <v-card-actions class="mt-4">
      <v-btn
        depressed
        color="primary"
        @click="downloadSpreadsheet"
        style="margin: 8px"
      >
        Download as Spreadsheet
      </v-btn>
    </v-card-actions>
    <v-card-actions class="mt-4">
      <v-file-input
        ref="inputFile"
        show-size
        truncate-length="50"
        @change="onFileChange"
      ></v-file-input>
      <v-btn
        :disabled="uploadFile == null"
        :loading="uploading === true"
        color="success"
        class="ma-2 white--text"
        @click="handleFileUpload"
      >
        Upload Spreadsheet
        <!-- <v-icon right dark>mdi-cloud-upload</v-icon> -->
      </v-btn>
    </v-card-actions>
    <v-card-actions class="mt-4">
      <v-btn
        depressed
        color="primary"
        @click="downloadSpreadsheet"
        style="margin: 8px"
      >
        Migrate inactive members
      </v-btn>
    </v-card-actions>
    <v-snackbar v-model="infoSnackbar">
      {{infoMessage}}
      <v-btn color="pink" text @click="infoSnackbar = false">
        Close
      </v-btn>
    </v-snackbar>
    <v-snackbar v-model="errorSnackbar">
      {{errorMessage}}
      <v-btn color="pink" text @click="errorSnackbar = false">
        Close
      </v-btn>
    </v-snackbar>
  </v-card>
</template>

<script>
  import axios from 'axios';

  export default {
    name: 'RefsetDetails',
    props: {
      selectedRefset: Object,
      mode: {
        type: String,
        default: 'refset'
      },
      refsetEndpoint: String
    },
    data: () => ({
      uploading: false,
      uploadFile: null,
      errorSnackbar: false,
      infoSnackbar: false,
      errorMessage: '',
      infoMessage: ''
    }),
    watch: {
      selectedRefset: function() {
        this.$refs.inputFile.reset();
      }
    },
    methods: {
      downloadSpreadsheet() {
        window.open('/api/refsets/' + this.refsetEndpoint + '/' + this.selectedRefset.conceptId + '/spreadsheet');
      },
      onFileChange(event) {
        if (event && event.lastModified) {
          this.uploadFile = event;
        } else {
          this.uploadFile = null;
        }
      },
      handleFileUpload() {
        console.log('upload');
        let formData = new FormData();
        formData.append('file', this.uploadFile);
        this.uploading = true;
        this.infoSnackbar = false;
        this.errorSnackbar = false;
        var context = this;
        axios.put('/api/refsets/' + this.refsetEndpoint + '/' + this.selectedRefset.conceptId + '/spreadsheet',
          formData,
          {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
          }
        ).then(function(response){
          console.log('SUCCESS!!');
          context.uploading = false;
          context.infoSnackbar = true;
          context.infoMessage = "Successful upload. " + response.data.added + " members added, " + response.data.updated + " updated, " + response.data.removed + " removed.";
          context.selectedRefset.activeMemberCount = response.data.newTotal;
        })
        .catch(function(e){
          console.log('FAILURE!!');
          context.uploading = false;
          if (e && e.response && e.response.data && e.response.data.message) {
            context.errorMessage = e.response.data.message;
          } else {
            context.errorMessage = "Upload failed.";
          }
          context.snackbar = true;
        });
      }
    }
  }
</script>
