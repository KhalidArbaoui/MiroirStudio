const BASE = 'https://cdn.myportfolio.com/e4dea4af-9f97-454b-9e73-4b40a2da4143';

export interface GalleryItem {
  id: number;
  src: string;
  title?: string;
  type?: 'image' | 'video';
  tall?: boolean;
}

export interface Category {
  id: string;
  title: string;
  description: string;
  cover: string;
  items: GalleryItem[];
}

export const categories: Category[] = [
  {
    id: 'food',
    title: 'Food Photography',
    description: 'Elevate your menu with captivating imagery that makes every dish irresistible. Perfect for restaurants, cafés, and food brands.',
    cover: './images/sushi-thumbnail.jpg',
    items: [
      { id: 1,  src: './images/DSC03556.jpg', title: 'Artisan Dish' },
      { id: 2,  src: './images/DSC03560.jpg', title: 'Culinary Creation' },
      { id: 3,  src: './images/DSC03688.jpg', title: 'Plated Perfection' },
      { id: 4,  src: './images/DSC03739.jpg', title: 'Gourmet Presentation' },
      { id: 5,  src: './images/DSC03832.jpg', title: 'Signature Plate' },
      { id: 6,  src: './images/DSC03936.jpg', title: 'Fine Dining' },
      { id: 7,  src: './images/DSC03991.jpg', title: 'Culinary Art' },
      { id: 8,  src: './images/DSC03997.jpg', title: 'Gourmet Series' },
      { id: 9,  src: './images/DSC04047.jpg', title: 'Chef Special' },
      { id: 10, src: './images/DSC04062.jpg', title: 'Tasteful Edit' },
      { id: 11, src: './images/DSC04070.jpg', title: 'Fresh Perspective' },
      { id: 12, src: './images/DSC04071.jpg', title: 'Kitchen Masterpiece' },
      { id: 13, src: './images/DSC04092.jpg', title: 'Food Styling' },
      { id: 14, src: './images/DSC04454.jpg', title: 'Elegant Plate' },
      { id: 15, src: './images/DSC04618.jpg', title: 'Mouthwatering' },
      { id: 16, src: './images/DSC04644.jpg', title: 'Crafted Cuisine' },
      { id: 17, src: './images/DSC04682-Recovered.jpg', title: 'Heritage Recipe' },
      { id: 18, src: './images/DSC04690.jpg', title: 'Final Touch' },
      { id: 19, src: `${BASE}/1d33ce28-567b-4a34-a99c-7883b1a51d65_rw_3840.png?h=9508a5e4949416fc08a07fbbfddca2dd`, title: 'Gourmet Capture' },
      { id: 20, src: `${BASE}/28189d9f-fe44-46ef-913d-ef6ea7d80ec9_rw_1920.png?h=ab38ecb0b539d936fe9343adc48bfbc3`, title: 'Dish Highlight' },
      { id: 21, src: `${BASE}/741d9b5d-9db1-4428-a94b-e327866aaf9b_rw_1920.png?h=0ff7a2e0ee7805d673cf653e2149c872`, title: 'Cinematic Edit' },
      { id: 22, src: './images/sushi/sushi 1.jpg', title: 'Sushi Art' },
      { id: 23, src: './images/sushi/sushi 2.jpg', title: 'Sushi Platter' },
      { id: 24, src: './images/sushi/sushi 3.jpg', title: 'Fresh Rolls' },
      { id: 25, src: './images/sushi/sushi 4.jpg', title: 'Sushi Selection' },
      { id: 26, src: './images/sushi/sushi 4 (2).jpg', title: 'Nigiri Set' },
      { id: 27, src: './images/sushi/sushi 5.jpg', title: 'Sashimi Platter' },
      { id: 28, src: './images/sushi/sushi 6.jpg', title: 'Dragon Roll' },
      { id: 29, src: './images/sushi/sushi 6 (2).jpg', title: 'Premium Sushi' },
    ],
  },
  {
    id: 'product',
    title: 'Product Photography',
    description: 'Showcase your products with precision and elegance. Professional product photography tailored for e-commerce and brand campaigns.',
    cover: './images/DSC00335.jpg',
    items: [
      { id: 1, src: './images/DSC00335.jpg', title: 'Product Shot' },
      { id: 2, src: './images/DSC08735.jpg', title: 'Product Shot' },
      { id: 3, src: './images/DSC02466.jpg', title: 'Product Shot' },
      { id: 4, src: './images/DSC02112.jpg', title: 'Product Shot' },
      { id: 5, src: './images/DSC02004.jpg', title: 'Product Shot' },
      { id: 6, src: './images/DSC01747-4.jpg', title: 'Product Shot' },
      { id: 7, src: './images/DSC01726-3.jpg', title: 'Product Shot' },
      { id: 8, src: './images/chocolate 4.jpg', title: 'Product Shot' },
      { id: 9, src: './images/chocolate 2.jpg', title: 'Product Shot' },
      { id: 10, src: './images/1.jpg', title: 'Product Shot' },
    ],
  },
  {
    id: 'reels',
    title: 'Reels & Video Content',
    description: 'Dynamic video content designed to captivate audiences on social media. Cinematic storytelling for your brand.',
    cover: './images/reels-cover.jpg',
    items: [
      { id: 1,  src: './images/bab dzair 2 .mp4',                type: 'video' },
      { id: 2,  src: './images/C0878.mp4',                        type: 'video' },
      { id: 3,  src: './images/C1045_1.mp4',                      type: 'video' },
      { id: 4,  src: './images/C1074_2.mp4',                      type: 'video' },
      { id: 5,  src: './images/GHAF VIDEO.mp4',                   type: 'video' },
      { id: 6,  src: './images/pouring chocolate.mp4',            type: 'video' },
    ],
  },
  {
    id: 'perfume',
    title: 'Perfume & Oud',
    description: 'Luxurious fragrance photography that captures the essence and sophistication of your perfume and oud collection.',
    cover: `${BASE}/55f2fcf7-0e81-4085-acf5-4498675a245a.png?h=7b4dcaddebcf5333879215496b290b7b`,
    items: [
      { id: 6,  src: `${BASE}/55f2fcf7-0e81-4085-acf5-4498675a245a.png?h=7b4dcaddebcf5333879215496b290b7b`, title: 'Fragrance Edit',    tall: true },
      { id: 7,  src: `${BASE}/d4941bfe-44cf-4b2b-a14c-827b2458663f.png?h=6e28790bde8d78f0fba037913ae839ca`, title: 'Oud Collection',    tall: false },
      { id: 8,  src: `${BASE}/8b5865e7-21b3-458a-97cf-0208901a6c56.png?h=748d987074dbcc63c77ad503b2d57a0d`, title: 'Luxury Scent',      tall: false },
      { id: 9,  src: `${BASE}/c8e7b51f-95b6-49e3-8c99-059a2e410991_rw_1920.png?h=7f96f1ae4b6dad9b181cfa5d34755201`, title: 'Arabic Fragrance',  tall: true },
      { id: 10, src: `${BASE}/924f1a33-6c12-48d4-8118-5e4ea4dc8d81_rw_1920.png?h=f9403e41566abf1fa0ff00e2d460a163`, title: 'Signature Oud',     tall: false },
    ],
  },
  {
    id: 'cosmetics',
    title: 'Cosmetics',
    description: 'Beauty and cosmetics photography that highlights texture, color, and elegance. Designed for brands that demand perfection.',
    cover: `${BASE}/a62cff8e-12d6-4f64-ac9c-83de50e0c0d8_rw_1920.png?h=67249c757c0b64fe0f8f999c6da06bb6`,
    items: [
      { id: 11, src: `${BASE}/a62cff8e-12d6-4f64-ac9c-83de50e0c0d8_rw_1920.png?h=67249c757c0b64fe0f8f999c6da06bb6`, title: 'Beauty Edit',       tall: false },
      { id: 12, src: `${BASE}/338aab3c-217c-4816-b58b-65568600294f_rw_3840.png?h=97c1ad5e83e5fb15229b5c8513b50d94`, title: 'Skincare Range',    tall: true },
      { id: 15, src: `${BASE}/d928a45a-bd09-4775-afb7-5177f97cefd2_rw_1920.png?h=f85f2f3f19c6ae5c1ec7939c85eb85ed`, title: 'Luxury Glow',       tall: false },
      { id: 16, src: `${BASE}/3606e2cb-aad5-454a-8df1-fab0ba825e08_rw_3840.png?h=5ba93af106ec6d5f763bd7c814eb634b`, title: 'Radiance Series',   tall: true },
      { id: 17, src: `${BASE}/ceab03cb-cf33-47fd-af30-3b44bfd2f997_rw_3840.png?h=a2e56dfd6c370f3518c87cb4e46f3a5b`, title: 'Signature Look',    tall: false },
    ],
  },
  {
    id: 'jewelry',
    title: 'Jewelry',
    description: 'Fine jewelry photography with meticulous attention to detail. Showcasing brilliance, texture, and exquisite craftsmanship.',
    cover: `${BASE}/76481676-ee8f-4890-b301-d9360ef539d8_rw_1920.JPG?h=815815c6d5052485dd0f8ea19d70efc8`,
    items: [
      { id: 13, src: `${BASE}/76481676-ee8f-4890-b301-d9360ef539d8_rw_1920.JPG?h=815815c6d5052485dd0f8ea19d70efc8`, title: 'Fine Jewelry',      tall: false },
      { id: 19, src: `${BASE}/6f9631bd-ae0c-4dff-a957-758539ec2485_rw_3840.png?h=40f65be2714ac892db4a8e097f081544`, title: 'Heritage Edit',     tall: true },
      { id: 14, src: `${BASE}/8809f5b9-8344-4d34-90c7-582349d73d8d_rw_1920.png?h=929b05ff5a40b95e79f0c8126bcd5ed6`, title: 'Gold Collection',   tall: false },
      { id: 18, src: `${BASE}/04343354-8091-4787-bf2f-2711418b90f0.png?h=b3ea94035d5472424e86999b525357a1`, title: 'Luxury Pieces',     tall: false },
    ],
  },
];
