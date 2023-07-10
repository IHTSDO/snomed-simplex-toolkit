<template>
  <v-card
    class="px-4"
    outlined
  >
    <v-list-item-content>
        <div class="text-overline mb-4">Selected Language/Dialect<span>...</span></div>
        <v-list-item-title class="text-h5 mb-1" ><b>{{selectedRefset.pt.term}}</b><br/>Concept ID: {{selectedRefset.conceptId}}</v-list-item-title>
        <v-list-item-subtitle>{{selectedRefset.activeMemberCount}} active members.</v-list-item-subtitle>
    </v-list-item-content>
    <v-card-actions class="mt-4">
      <v-radio-group v-model="translationTool">
        <v-radio value="weblate" label="Use Weblate"/>
        <v-radio value="refset-tool" label="Use Refset & Translation Tool"/>
      </v-radio-group>
    </v-card-actions>
    <div v-if="!job && translationTool == 'weblate'">
      <v-card-actions class="mt-4">
        <div>
          <p>
            <b>How does it work?</b>
          </p>
          <p>
            Use <a href="https://translate.snomedtools.org/" target="_blank">Weblate</a> to create concept translations.
            Export the translation and upload it using the form below.
            The translated terms will be applied within your edition.
          </p>
        </div>
      </v-card-actions>
      <v-card-actions class="mt-4">
        <v-autocomplete
          :disabled="languageAlreadySet"
          label="Language"
          v-model="selectedLanguage"
          :items="languages"
          item-text="name"
          item-value="code"
        >
        </v-autocomplete>
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
        </v-btn>
      </v-card-actions>
    </div>
    <div v-if="!job && translationTool == 'refset-tool'">
      <v-card-actions class="mt-4">
        <div>
          <p>
            <b>How does it work?</b>
          </p>
          <p>
            Use the <a href="https://refset.ihtsdotools.org/" target="_blank">Refset & Translation</a> tool to create concept translations.
            Export the translation and upload it using the form below.
            The translated terms will be applied within your edition.
          </p>
        </div>
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
          Upload Translation Export
        </v-btn>
      </v-card-actions>
    </div>
    <v-card-actions class="mt-4" v-if="job" style="padding-bottom: 25px;">
      <v-list-item-content>
        <b>Processing {{ job.recordsTotal }} rows</b>
      </v-list-item-content>
      <v-list-item-content>
        <b>{{ job.status }}</b>
      </v-list-item-content>
      <v-progress-circular
        :rotate="-90"
        :size="100"
        :width="15"
        :value="job.percentage"
        color="primary"
      >
        {{ job.percentage }}
      </v-progress-circular>
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
    name: 'TranslationDetails',
    props: {
      selectedRefset: Object,
      codeSystem: Object
    },
    data: () => ({
      uploading: false,
      uploadFile: null,
      selectedLanguage: "",
      languageAlreadySet: false,
      languages: [],
      errorSnackbar: false,
      infoSnackbar: false,
      errorMessage: '',
      infoMessage: '',
      job: null,
      jobProgress: 0,
      trackJobInterval: null,
      translationTool: null,
    }),
    watch: {
      selectedRefset: function() {
        if (this.$refs.inputFile) {
          this.$refs.inputFile.reset();
        }
        this.checkLanguageLock();
        this.trackJobProgress();
      }
    },
    mounted() {
      let context = this;
      this.checkLanguageLock()
      axios
        .get('api/language-codes')
        .then(function(response) {
          context.languages = response.data
        })
        this.trackJobProgress()
    },
    methods: {
      checkLanguageLock() {
        if (this.selectedRefset.lang) {
          this.selectedLanguage = this.selectedRefset.lang
          this.languageAlreadySet = true;
        } else {
          this.selectedLanguage = ""
          this.languageAlreadySet = false;
        }
      },
      downloadSpreadsheet() {
        window.open('api/' + this.codeSystem.shortName + '/translations/' + this.selectedRefset.conceptId + '/spreadsheet');
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
        if (this.translationTool == 'weblate') {
          formData.append('languageCode', this.selectedLanguage);
        }
        this.uploading = true;
        this.infoSnackbar = false;
        this.errorSnackbar = false;
        var context = this;
        axios.put('api/' + this.codeSystem.shortName + '/translations/' + this.selectedRefset.conceptId + '/' + this.translationTool,
          formData,
          {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
          }
        ).then(function(){
          context.uploading = false;
          context.trackJobProgress();
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
      },
      trackJobProgress() {
        if (this.trackJobInterval) {
          console.log('clearInterval')
          clearInterval(this.trackJobInterval)
          this.job = null;
        }
        let context = this;
        axios
          .get('api/jobs?refsetId=' + this.selectedRefset.conceptId)
          .then(function(response) {
            if (response.data.length && response.data[0].status != 'COMPLETED') {
              context.job = response.data[0]
              context.trackJobInterval = setInterval(function() {
                console.log('job status fetch')
                axios
                  .get('api/jobs/' + context.job.id)
                  .then(function(response) {
                    let job = response.data
                    job.percentage = Math.round((job.recordsProcessed / job.recordsTotal) * 100)
                    context.job = job;
                    if (job.status == 'FAILED' || job.status == 'COMPLETED') {
                      clearInterval(context.trackJobInterval)
                      context.job = null;
                      console.log('job status fetch - clear')
                      context.infoSnackbar = true;
                      context.infoMessage = "Successful upload.";
                      context.selectedRefset.activeMemberCount = response.data.changeSummary.newTotal;
                    }
                  })
              }, 1000)
            }
          })
      }
    }
  }
</script>
