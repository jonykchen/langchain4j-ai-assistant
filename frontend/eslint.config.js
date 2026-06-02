import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'
import tsParser from '@typescript-eslint/parser'
import { FlatCompat } from '@eslint/eslintrc'
import path from 'path'
import { fileURLToPath } from 'url'
import globals from 'globals'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const compat = new FlatCompat({
  baseDirectory: __dirname,
})

// Get environment mode
const isProduction = process.env.NODE_ENV === 'production'

export default [
  {
    name: 'app/files-to-ignore',
    ignores: ['**/dist/**', '**/dist-ssr/**', '**/coverage/**', '**/node_modules/**'],
  },

  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],

  // Use FlatCompat to convert traditional configs
  ...compat.extends('@vue/eslint-config-typescript'),
  ...compat.extends('@vue/eslint-config-prettier'),

  // Vue files configuration
  {
    name: 'app/vue',
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        extraFileExtensions: ['.vue'],
        parser: {
          ts: tsParser,
          js: 'espree',
          '<template>': 'espree',
        },
      },
      globals: {
        ...globals.browser,
        ...globals.node,
        process: 'readonly',
      },
    },
  },

  // TypeScript files configuration
  {
    name: 'app/typescript',
    files: ['**/*.{ts,mts,tsx}'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
      globals: {
        ...globals.browser,
        ...globals.node,
        process: 'readonly',
      },
    },
  },

  // ESLint config files configuration
  {
    name: 'app/eslint-config',
    files: ['**/eslint.config.js'],
    languageOptions: {
      globals: {
        ...globals.node,
      },
    },
  },

  {
    name: 'app/rules',
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      'no-console': isProduction ? 'warn' : 'off',
      'no-debugger': isProduction ? 'warn' : 'off',
    },
  },
]
