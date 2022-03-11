<template>
  <v-form v-model="valid">
    <v-container>
      <v-row>
        <h3 class="mt-4">Export and Release tools</h3>
      </v-row>
      <v-row>
        <v-col cols="12" md="6">
          <br><br><br>
          <v-btn
            depressed
            color="primary"
            width="60%"
          >
            Run QA Tests
          </v-btn>
          <br><br>
          <v-btn
            depressed
            color="primary"
            width="60%"
          >
            Export RF2 Snapshot
          </v-btn>
          <br><br>
          <v-btn
            depressed
            color="primary"
            width="60%"
          >
            Mark Snapshot as new release
          </v-btn>
          <br><br>
          <v-btn
            depressed
            color="primary"
            width="60%"
          >
            Download latest release ({{latestRelease}})
          </v-btn>
        </v-col>
      </v-row>
    </v-container>
  </v-form>
</template>

<script>
  export default {
    name: 'ExportManager',
    data: () => ({
      latestRelease: '20210815'
    }),
    methods: {
  },
  }
</script>