<template>
  <v-form v-model="valid">
    <v-container>
      <v-row>
        <h3 class="mt-4" v-if="!connected">Connect to Snowstorm server</h3>
        <h3 class="mt-4" v-if="connected">Connected!</h3>
      </v-row>
      <v-row>
        <v-col cols="6" md="6">
          <v-row class="mt-4">
            <v-col cols="10" md="10">
              <v-text-field
                v-model="serverUrl"
                :rules="urlRules"
                label="Server URL:"
                :disabled="connected"
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
                :disabled="dataRetrieved || serverUrl == ''"
                :loading="retrievingData"
              >
                Get data
              </v-btn>
            </v-col>
          </v-row>
        
        <!-- :items="['New Code System'].concat(codeSystems)" -->
        <v-combobox
          v-model="authoringCodeSystem"
          :items="codeSystems"
          label="Authoring Code System"
          :rules="[required]"
          outlined
          dense
          :disabled="connected || !dataRetrieved"
        ></v-combobox>
        <v-row v-if="authoringCodeSystem == 'New Code System'">
           <v-col>
            <v-combobox
              v-model="dependency"
              :items="codeSystems"
              label="Dependenciy (Select Code System)"
              :rules="[required]"
              outlined
              dense
              :disabled="connected || !dataRetrieved"
            ></v-combobox>
            <v-combobox
              v-model="dependencyVersion"
              :items="versions"
              label="Dependenciy Version"
              :rules="[required]"
              outlined
              dense
              :disabled="connected || !dataRetrieved"
            ></v-combobox>
            <v-text-field
              v-model="newCodeSystemName"
              :rules="newCodeSystemNameRules"
              label="New Code System Name:"
              required
              :disabled="connected || !dataRetrieved"
            ></v-text-field>
            <v-text-field
              v-model="newCodeSystemShortName"
              :rules="newCodeSystemShortNameRules"
              label="New Code System Short Name:"
              :counter=20
              required
              :disabled="connected || !dataRetrieved"
            ></v-text-field>
            <v-text-field
              v-model="namespace"
              :rules="namespaceRules"
              label="Namespace:"
              :counter=7
              type="number"
              required
              :disabled="connected || !dataRetrieved"
            ></v-text-field>
           </v-col>
        </v-row>
         <v-combobox
          v-model="extensionLanguageRefset"
          :items="languageRefsets"
          label="Language Refset"
          :rules="[required]"
          outlined
          dense
          :disabled="connected || !dataRetrieved"
        ></v-combobox>
        <v-text-field
          v-model="extensionLanguageCode"
          :rules="extensionLanguageCodeRules"
          label="Language Code:"
          :counter=2
          type="text"
          required
          :disabled="connected || !dataRetrieved"
        ></v-text-field>
        <br><br><br>
        <v-btn
          v-if="!connected && !connecting"
          :disabled="!valid"
          depressed
          color="primary"
          @click="connect(true)"
        >
          Connect to Snowstorm
        </v-btn>
        <v-progress-circular
          v-if="connecting"
          indeterminate
          color="amber"
        ></v-progress-circular>
        <span class="mx-4 amber--text" transition="slide-x-transition">{{connectingMessage}}</span>
        <v-btn
          v-if="connected && !connecting"
          :disabled="!valid"
          depressed
          color="error"
          @click="connect(false)"
        >
          Disconnect from Snowstorm
        </v-btn>
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
  export default {
    data: () => ({
      valid: false,
      dependency: '',
      dependencyVersion: '',
      versions: [],
      codeSystems: [],
      languageRefsets: [],
      extensionLanguageRefset: '',
      extensionLanguageCode: '',
      extensionLanguageCodeRules: [v => v.length == 2 || 'Must be 2 chars long (ISO Language Code)'],
      serverUrl: '',
      urlRules: [
        v => !!v || 'URL is required',
        v => /.+\..+/.test(v) || 'URL must be valid',
      ],
      authoringCodeSystem: '',
      newCodeSystemName: '',
      newCodeSystemNameRules: [v => v.length >= 5 && v.length <= 255 || 'Min 5 characters, max 255 characters'],
      newCodeSystemShortName: '',
      newCodeSystemShortNameRules: [v => v.length <= 20 || 'Max 20 characters long'],
      namespace: '',
      namespaceRules: [v => v.length == 7 || 'Must be 7 nums long'],
      connected: false,
      connecting: false,
      connectingMessage: '',
      dataRetrieved: false,
      retrievingData: false,
      snackbar: false,
      snackText: 'My timeout is set to 2000.',
      timeout: 2000,
    }),
    methods: {
    required(value) {
      if (value instanceof Array && value.length == 0) {
        return 'Required.';
      }
      return !!value || 'Required.';
    },
    getData() {
      var context = this;
      this.retrievingData = true;
      setTimeout(function() {
        context.retrievingData = false;
        context.dataRetrieved = true;
        context.snackText = 'Data retrieved'
        context.snackbar = true;
        context.languageRefsets = [
          'US English',
          'GB English',
          'English',
          'French [International Organization for Standardization 639-1 code fr] language reference set',
          'German [International Organization for Standardization 639-1 code de] language reference set',
          'Japanese [International Organization for Standardization 639-1 code ja] language reference set',
          'Chinese [International Organization for Standardization 639-1 code zh] language reference set',
          'GMDN language reference set',
          'Spanish language reference set'
        ];
        context.codeSystems = [
          'SCT International Edition',
          'SCT Spanish Edition',
          'SCT US Edition',
          'SCT UK Edition',
          'SCT Australia Edition',
          'SCT Argentina Edition'
        ];
        context.versions = [
          '20220131',
          '20210731',
          '20210131',
          '20200731',
          '20200131'
        ]
      }, 1000);
    },
    connect(val) {
      var context = this;
      if (val) {
        if (this.authoringCodeSystem == 'New Code System') {
          this.connecting = true;
          this.connectingMessage = 'Creating Kernel...';
          setTimeout(function() {
            context.connectingMessage = 'Connecting...';
            setTimeout(function() {
              context.connectingMessage = 'Uploading kernel...';
              setTimeout(function() {
                context.connected = val;
                context.connecting = false;
                context.connectingMessage = '';
                context.$emit('connected', context.connected);
                var connectionDetails = {
                  serverUrl: context.serverUrl,
                  dependency: context.dependency,
                  authoringCodeSystem: context.authoringCodeSystem,
                  newCodeSystemName: context.newCodeSystemName,
                  newCodeSystemShortName: context.newCodeSystemShortName,
                  extensionLanguageRefset: context.extensionLanguageRefset,
                  extensionLanguageCode: context.extensionLanguageCode
                }
                context.$emit('connectionDetails', connectionDetails);
                context.snackText = 'Connected'
                context.snackbar = true;
              }, 1000);
            }, 1000);
          }, 1000);
        } else {
          this.connecting = true;
          this.connectingMessage = 'Connecting...';
          setTimeout(function() {
            context.connected = val;
            context.connecting = false;
            context.connectingMessage = '';
            context.$emit('connected', context.connected);
            var connectionDetails = {
                  serverUrl: context.serverUrl,
                  dependency: context.dependency,
                  authoringCodeSystem: context.authoringCodeSystem,
                  extensionLanguageRefset: context.extensionLanguageRefset,
                  extensionLanguageCode: context.extensionLanguageCode
                }
                context.$emit('connectionDetails', connectionDetails);
                context.snackText = 'Connected'
                context.snackbar = true;
          }, 1000);
        }
      } else {
        this.connecting = true;
        this.connectingMessage = 'Disconnecting...';
        setTimeout(function() {
          context.connected = val;
          context.connecting = false;
          context.connectingMessage = '';
          context.$emit('connected', context.connected);
          context.snackText = 'Disconnected'
          context.snackbar = true;
        }, 1000);
      }
    }
  },
  }
</script>