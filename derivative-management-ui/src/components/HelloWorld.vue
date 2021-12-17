<template>
  <v-container>
    <v-row class="text-center">
      <v-col class="mb-4">
        <h1 class="display-2 font-weight-bold mb-3">
          Welcome
        </h1>
      </v-col>

      <v-col
        class="mb-5"
        cols="12"
      >
        <h2 class="headline font-weight-bold mb-3">
          What's next?
        </h2>

        <v-row justify="center">
          <a
            v-for="(next, i) in whatsNext"
            :key="i"
            :href="next.href"
            class="subheading mx-3"
            target="_blank"
          >
            {{ next.text }}
          </a>
        </v-row>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
  export default {
    name: 'HelloWorld',

    data: () => ({
      whatsNext: [
        {
          text: 'Vuetify Grid system',
          href: 'https://vuetifyjs.com/en/components/grids/',
        },
        {
          text: 'Vuetify Application component',
          href: 'https://vuetifyjs.com/en/components/application/',
        },
      ],
    }),
  }
</script>
<style scoped>
  h1 {
    margin-top: 100px;
  }
</style>
