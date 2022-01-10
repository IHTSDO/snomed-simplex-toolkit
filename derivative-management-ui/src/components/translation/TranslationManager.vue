<template>
  <v-container>
    <v-row>
      <h3 class="mt-4">Translation Manager</h3>
    </v-row>
    <v-row>
      <v-col cols="4" md="4">
        <h4 class="mt-4">Translation progress:</h4>
        <br>
        <v-card
          outlined
        >
          <v-col cols="12" md="12">
            <p>Number of translated concepts: 1209</p>
            <p>Number of FSNs: 845</p>
            <p>Number of preferred synonyms: 1209</p>
            <p>Number of acceptable synonyms: 124</p>
          </v-col>
        </v-card>
      </v-col>
      <v-col cols="6" md="6">
        Refsets:
        <SelectableRefsetsList @selectedRefsets="captureSelection" />
        <br>
        <v-btn
          depressed
          color="primary"
          :disabled="!selectedRefsets || !selectedRefsets.length"
        >
          Donwload Translation Template for selected Refsets
        </v-btn>
        <br><br>
        <v-btn
          depressed
          color="success"
        >
          Upload translation update
        </v-btn>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
  import SelectableRefsetsList from '../refsets/SelectableRefsetsList.vue';

  export default {
    name: 'TranslationManager',
    data: () => ({
      selectedRefsets: null,
    }),
    components: {
      SelectableRefsetsList,
    },
    methods: {
      captureSelection(refsets) {
        this.selectedRefsets = refsets;
        // alert(this.selectedRefsets)
      }
    }
  }
</script>