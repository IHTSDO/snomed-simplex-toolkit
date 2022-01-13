<template>
  <v-card
    class="px-4"
    outlined
  >
    <v-list-item-content>
        <div class="text-overline mb-4" v-text="mode"></div>
        <v-list-item-title class="text-h5 mb-1" v-text="refsetName"></v-list-item-title>
        <v-list-item-subtitle v-text="refsetSubt"></v-list-item-subtitle>
      </v-list-item-content>
      <v-card-actions class="mt-4">
      <v-btn
        depressed
        color="primary"
      >
        Download {{ mode }} (XLSX)
      </v-btn>
      <v-btn
        depressed
        color="success"
      >
        Upload new version (XLSX)
      </v-btn>
    </v-card-actions>
  </v-card>
</template>

<script>
  export default {
    name: 'RefsetDetails',
    props: {
      refsetName: String,
      refsetSubt: String,
      mode: {
        type: String,
        default: 'refset'
      }
    },
    data: () => ({
    }),
  }
</script>