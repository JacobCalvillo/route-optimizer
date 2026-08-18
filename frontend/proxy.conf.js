// Dev-server proxy. A .js config (rather than .json) so the backend URL can come from
// frontend/.env — handy when port 8080 is taken and you moved the backend elsewhere.
require('dotenv').config();

const target = process.env.BACKEND_URL || 'http://localhost:8080';

module.exports = {
  '/api': {
    target,
    secure: false,
    changeOrigin: true,
  },
};
