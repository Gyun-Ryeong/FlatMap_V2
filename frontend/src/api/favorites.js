import axiosInstance from './axiosInstance';

export async function getFavorites() {
  const res = await axiosInstance.get('/favorites');
  return res.data;
}

export async function addFavorite(place) {
  const res = await axiosInstance.post('/favorites', {
    name: place.name,
    address: place.address,
    latitude: place.lat,
    longitude: place.lng,
  });
  return res.data;
}

export async function removeFavorite(id) {
  await axiosInstance.delete(`/favorites/${id}`);
}
