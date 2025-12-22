import http from 'k6/http';

console.log('--- Lyocell Smoke Test ---');
console.log('Executing GET request...');

const res = http.get('https://httpbin.org/get');

console.log('Response status:', res.status);
console.log('Response body snippet:', res.body.substring(0, 50) + '...');

console.log('--- Smoke Test Finished ---');
