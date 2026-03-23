import { defineConfig } from 'cypress'

export default defineConfig({
  
  e2e: {
    'baseUrl': 'http://localhost:4200',
    'defaultCommandTimeout': 10_000,
    'requestTimeout': 15_000,
    'env': {
      'username': 'test',
      'password': 'test',
    }
  },
  
})
