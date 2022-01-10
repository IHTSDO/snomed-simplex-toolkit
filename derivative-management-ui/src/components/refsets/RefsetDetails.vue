<template>
  <v-card
    class="px-4"
    outlined
  >
    <v-list-item-content>
        <div class="text-overline mb-4">
          Refset
        </div>
        <v-list-item-title class="text-h5 mb-1" v-text="refset.name"></v-list-item-title>
        <v-list-item-subtitle v-text="refset.subtitle"></v-list-item-subtitle>
      </v-list-item-content>
      <v-card-actions class="mt-4">
      <v-btn
        depressed
        color="primary"
      >
        Donwload refset (XLSX)
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
    data: () => ({
      name: 'RefsetDetails',
      refset: {name: 'COVID Vaccines', subtitle: '14 concepts, last update Dec. 12th 2021'}
    }),
  }
</script>