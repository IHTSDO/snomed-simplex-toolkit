<template>
  <v-form v-model="valid">
    <v-container>
      <v-row>
        <h3 class="mt-4">Connect to Snowstorm server</h3>
      </v-row>
      <v-row>
        <v-col cols="12" md="6">
          <v-text-field
            v-model="serverUrl"
            :rules="urlRules"
            label="Server URL:"
            required
          ></v-text-field>
          <v-combobox
          v-model="dependency"
          :items="codeSystems"
          label="Dependenciy (Select Code System)"
          :rules="[required]"
          outlined
          dense
        ></v-combobox>
        <v-combobox
          v-model="authoringCodeSystem"
          :items="['New Code System'].concat(codeSystems)"
          label="Auhtoring Code System"
          :rules="[required]"
          outlined
          dense
        ></v-combobox>
        <v-row v-if="authoringCodeSystem == 'New Code System'">
           <v-col>
            <v-text-field
              v-model="newCodeSystemName"
              :rules="newCodeSystemNameRules"
              label="New Code System Name:"
              required
            ></v-text-field>
            <v-text-field
              v-model="newCodeSystemShortName"
              :rules="newCodeSystemShortNameRules"
              label="New Code System Short Name:"
              :counter=20
              required
            ></v-text-field>
            <v-text-field
              v-model="namespace"
              :rules="namespaceRules"
              label="Namespace:"
              :counter=7
              type="number"
              required
            ></v-text-field>
           </v-col>
        </v-row>
        <br><br><br>
        <v-btn
          :disabled="!valid"
          depressed
          color="primary"
        >
          Connect to Snowstorm
        </v-btn>
        </v-col>
      </v-row>
    </v-container>
  </v-form>
</template>

<script>
  export default {
    data: () => ({
      valid: false,
      dependency: '',
      codeSystems: [
        'SCT International Edition',
        'SCT Spanish Edition',
        'SCT US Edition',
        'SCT UK Edition',
        'SCT Australia Edition',
        'SCT Argentina Edition'
      ],
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
    }),
    methods: {
    required(value) {
      if (value instanceof Array && value.length == 0) {
        return 'Required.';
      }
      return !!value || 'Required.';
    },
  },
  }
</script>