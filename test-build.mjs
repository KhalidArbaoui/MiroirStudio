import { build } from 'vite';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const result = await build({
  root: __dirname,
  logLevel: 'info',
  build: {
    outDir: 'dist-test',
    emptyOutDir: true,
  }
});

const fs = await import('fs');
console.log('Build done. Files in dist-test:');
console.log(fs.readdirSync(path.join(__dirname, 'dist-test')));
