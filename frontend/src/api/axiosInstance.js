import axios from 'axios';

// 경로 조회는 T Map 후보 3종을 순차 호출하므로 넉넉한 타임아웃을 둔다.
const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  timeout: 30000,
});

export default axiosInstance;
