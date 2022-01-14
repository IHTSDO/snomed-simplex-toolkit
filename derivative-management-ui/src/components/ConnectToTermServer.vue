<template>
  <v-form v-model="valid">
    <v-container>
      <v-row>
        <h3 class="mt-4" v-if="!connectFailed && !connected">Connect to Snowstorm server</h3>
        <h3 class="mt-4" v-if="connectFailed">Connection to Snowstorm failed. Please check URL.</h3>
        <h3 class="mt-4" v-if="connected">Connected to Snowstorm</h3>
      </v-row>
      <v-row>
        <v-col cols="6" md="6">
          <v-row class="mt-4">
            <v-col cols="10" md="10">
              <v-text-field
                v-model="appConfig.snowstormUrl"
                :rules="urlRules"
                label="Server URL:"
                required
              ></v-text-field>
            </v-col>
            <v-col cols="2" md="2">
              <v-btn
                depressed
                small
                color="accent"
                @click="getData()"
                class="mt-4"
                :loading="retrievingData"
              >
                Set URL
              </v-btn>
            </v-col>
          </v-row>
          <v-row>
            <v-combobox
              v-model="authoringCodeSystem"
              :items="codeSystems"
              label="Authoring Code System"
              item-text="label"
              item-value="shortName"
              :rules="[required]"
              outlined
              dense
              :disabled="codeSystems.length === 0"
            ></v-combobox>
          </v-row>
          <v-row>
            <v-progress-circular
              v-if="connecting"
              indeterminate
              color="amber"
            ></v-progress-circular>
            <span class="mx-4 amber--text" transition="slide-x-transition">{{connectingMessage}}</span>
          </v-row>
          <v-row>
            <v-btn
            :disabled="!authoringCodeSystem || this.codeSystems.length == 0"
            @click="completeSetup()"
            >
              Save Configuration
            </v-btn>
          </v-row>
        </v-col>
      </v-row>
    </v-container>
    <v-snackbar
      v-model="snackbar"
      :timeout="timeout"
    >
      {{ snackText }}

      <template v-slot:action="{ attrs }">
        <v-btn
          color="blue"
          text
          v-bind="attrs"
          @click="snackbar = false"
        >
          Close
        </v-btn>
      </template>
    </v-snackbar>
  </v-form>
</template>

<script>
  import axios from 'axios';

  export default {
    data: () => ({
      appConfig: {
        snowstormUrl: '',
        codesystem: ''
      },
      valid: false,
      dependency: '',
      dependencyVersion: '',
      versions: [],
      codeSystems: [],
      languageRefsets: [],
      extensionLanguageRefset: '',
      extensionLanguageCode: '',
      extensionLanguageCodeRules: [v => v.length == 2 || 'Must be 2 chars long (ISO Language Code)'],
      urlRules: [
        v => !!v || 'URL is required',
        // v => /.+\..+/.test(v) || 'URL must be valid',
      ],
      authoringCodeSystem: '',
      newCodeSystemName: '',
      newCodeSystemNameRules: [v => v.length >= 5 && v.length <= 255 || 'Min 5 characters, max 255 characters'],
      newCodeSystemShortName: '',
      newCodeSystemShortNameRules: [v => v.length <= 20 || 'Max 20 characters long'],
      namespace: '',
      namespaceRules: [v => v.length == 7 || 'Must be 7 nums long'],
      connected: false,
      connectFailed: false,
      connecting: false,
      connectingMessage: '',
      dataRetrieved: false,
      retrievingData: false,
      snackbar: false,
      snackText: 'My timeout is set to 2000.',
      timeout: 2000,
    }),
    mounted: function() {
      this.loadConfig();
    },
    methods: {
    required(value) {
      if (value instanceof Array && value.length == 0) {
        return 'Required.';
      }
      return !!value || 'Required.';
    },
    loadConfig() {
      fetch("/api/app-config")
        .then(response => response.json())
        .then(data => {
          this.appConfig = data;
          if (this.appConfig.codesystem) {
            this.getData();
          }
        });
    },
    saveConfig() {
      return new Promise((resolve, reject) => {
        if (this.authoringCodeSystem) {
          this.appConfig.codesystem = this.authoringCodeSystem.shortName;
        }
        const requestOptions = {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(this.appConfig)
          };
        fetch("/api/app-config", requestOptions)
          .then(({data}) => {
            resolve(data);
          })
          .catch(error => {
            reject(error);
          })
      });
    },
    getData() {
      // var context = this;
      this.retrievingData = true;

      this.saveConfig()
        .then(() => {
          axios
            .get("/api/codesystems")
            .then(response => {
              this.codeSystems = response.data.items;
              this.codeSystems.forEach(codesystem => {
                codesystem.label = codesystem.shortName;
                if (!codesystem.name && codesystem.shortName === "SNOMEDCT") {
                  codesystem.name = "International Edition"
                }
                if (codesystem.name) {
                  codesystem.label += ", " + codesystem.name;
                }
                if (codesystem.shortName === this.appConfig.codesystem) {
                  this.authoringCodeSystem = codesystem;
                }
              })

              this.retrievingData = false;
              this.dataRetrieved = true;
              this.connected = true;
              this.connectFailed = false;
            })
            .catch(() => {
              this.retrievingData = false;
              this.connectFailed = true;
              this.codeSystems = [];
            })
        })
    },
    completeSetup() {
      this.saveConfig()
        .then(() => {
          this.$emit('connected', true);
        })
    }
  },
  }
</script>