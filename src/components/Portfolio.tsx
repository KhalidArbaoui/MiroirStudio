import { useState, useCallback } from 'react';
import type { MouseEvent } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, ArrowLeft, ArrowRight } from 'lucide-react';

const BASE = 'https://cdn.myportfolio.com/e4dea4af-9f97-454b-9e73-4b40a2da4143';

const categories = ['All', 'Food', 'Perfume & Oud', 'Cosmetics', 'Jewelry'];

const items = [
  { id: 1,  src: `${BASE}/28189d9f-fe44-46ef-913d-ef6ea7d80ec9_rw_1920.png?h=ab38ecb0b539d936fe9343adc48bfbc3`, category: 'Food',         title: 'Signature Dish',    tall: true  },
  { id: 2,  src: `${BASE}/0e708f62-3037-4f71-98e5-837b75b113e8_rw_1920.png?h=1f0fbf08e0ed76777d53d58706f5cd2d`, category: 'Food',         title: 'Culinary Art',      tall: false },
  { id: 3,  src: `${BASE}/f58fa964-88fb-410c-ad49-06bef30fd2b3_rw_1920.png?h=72229ec4fabc3f06de3987b7c1794fb3`, category: 'Food',         title: 'Plated Perfection', tall: false },
  { id: 4,  src: `${BASE}/741d9b5d-9db1-4428-a94b-e327866aaf9b_rw_1920.png?h=0ff7a2e0ee7805d673cf653e2149c872`, category: 'Food',         title: 'Fine Dining',       tall: true  },
  { id: 5,  src: `${BASE}/1d33ce28-567b-4a34-a99c-7883b1a51d65_rw_3840.png?h=9508a5e4949416fc08a07fbbfddca2dd`, category: 'Food',         title: 'Gourmet Series',    tall: false },
  { id: 6,  src: `${BASE}/55f2fcf7-0e81-4085-acf5-4498675a245a.png?h=7b4dcaddebcf5333879215496b290b7b`,  category: 'Perfume & Oud', title: 'Fragrance Edit',    tall: true  },
  { id: 7,  src: `${BASE}/d4941bfe-44cf-4b2b-a14c-827b2458663f.png?h=6e28790bde8d78f0fba037913ae839ca`,  category: 'Perfume & Oud', title: 'Oud Collection',    tall: false },
  { id: 8,  src: `${BASE}/8b5865e7-21b3-458a-97cf-0208901a6c56.png?h=748d987074dbcc63c77ad503b2d57a0d`,  category: 'Perfume & Oud', title: 'Luxury Scent',      tall: false },
  { id: 9,  src: `${BASE}/c8e7b51f-95b6-49e3-8c99-059a2e410991_rw_1920.png?h=7f96f1ae4b6dad9b181cfa5d34755201`, category: 'Perfume & Oud', title: 'Arabic Fragrance',  tall: true  },
  { id: 10, src: `${BASE}/924f1a33-6c12-48d4-8118-5e4ea4dc8d81_rw_1920.png?h=f9403e41566abf1fa0ff00e2d460a163`, category: 'Perfume & Oud', title: 'Signature Oud',     tall: false },
  { id: 11, src: `${BASE}/a62cff8e-12d6-4f64-ac9c-83de50e0c0d8_rw_1920.png?h=67249c757c0b64fe0f8f999c6da06bb6`, category: 'Cosmetics',     title: 'Beauty Edit',       tall: false },
  { id: 12, src: `${BASE}/338aab3c-217c-4816-b58b-65568600294f_rw_3840.png?h=97c1ad5e83e5fb15229b5c8513b50d94`, category: 'Cosmetics',     title: 'Skincare Range',    tall: true  },
  { id: 15, src: `${BASE}/d928a45a-bd09-4775-afb7-5177f97cefd2_rw_1920.png?h=f85f2f3f19c6ae5c1ec7939c85eb85ed`, category: 'Cosmetics',     title: 'Luxury Glow',       tall: false },
  { id: 16, src: `${BASE}/3606e2cb-aad5-454a-8df1-fab0ba825e08_rw_3840.png?h=5ba93af106ec6d5f763bd7c814eb634b`, category: 'Cosmetics',     title: 'Radiance Series',   tall: true  },
  { id: 17, src: `${BASE}/ceab03cb-cf33-47fd-af30-3b44bfd2f997_rw_3840.png?h=a2e56dfd6c370f3518c87cb4e46f3a5b`, category: 'Cosmetics',     title: 'Signature Look',    tall: false },
  { id: 13, src: `${BASE}/76481676-ee8f-4890-b301-d9360ef539d8_rw_1920.JPG?h=815815c6d5052485dd0f8ea19d70efc8`,  category: 'Jewelry',       title: 'Fine Jewelry',      tall: false },
  { id: 19, src: `${BASE}/6f9631bd-ae0c-4dff-a957-758539ec2485_rw_3840.png?h=40f65be2714ac892db4a8e097f081544`, category: 'Jewelry',       title: 'Heritage Edit',     tall: true  },
  { id: 14, src: `${BASE}/8809f5b9-8344-4d34-90c7-582349d73d8d_rw_1920.png?h=929b05ff5a40b95e79f0c8126bcd5ed6`, category: 'Jewelry',       title: 'Gold Collection',   tall: false },
  { id: 18, src: `${BASE}/04343354-8091-4787-bf2f-2711418b90f0.png?h=b3ea94035d5472424e86999b525357a1`,  category: 'Jewelry',       title: 'Luxury Pieces',     tall: false },
];

type Item = typeof items[0];

export default function Portfolio() {
  const [activeCategory, setActiveCategory] = useState('All');
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  const filtered = activeCategory === 'All' ? items : items.filter(i => i.category === activeCategory);
  const lightboxItem = lightboxIndex !== null ? filtered[lightboxIndex] : null;

  const prev = useCallback((e: MouseEvent) => {
    e.stopPropagation();
    setLightboxIndex(i => i !== null ? (i - 1 + filtered.length) % filtered.length : null);
  }, [filtered.length]);

  const next = useCallback((e: MouseEvent) => {
    e.stopPropagation();
    setLightboxIndex(i => i !== null ? (i + 1) % filtered.length : null);
  }, [filtered.length]);

  const col  = (n: number) => filtered.filter((_, i) => i % 3 === n);
  const col2 = (n: number) => filtered.filter((_, i) => i % 2 === n);

  return (
    <section id="portfolio" className="bg-[#080808] pt-28 pb-0">
      <div className="container-site">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="mb-14"
        >
          <div className="text-center">
            <div className="flex items-center justify-center gap-4 mb-6">
              <div className="w-6 h-px bg-[#c9a96e]" />
              <span className="font-sans text-[0.52rem] tracking-[0.55em] text-[#c9a96e] uppercase">Our Work</span>
              <div className="w-6 h-px bg-[#c9a96e]" />
            </div>
            <h2 className="font-serif text-5xl md:text-6xl text-[#f0ebe0] italic mb-12">Portfolio</h2>
            <div className="flex flex-wrap items-center justify-center gap-2">
              {categories.map(cat => (
                <button
                  key={cat}
                  onClick={() => setActiveCategory(cat)}
                  className={`font-sans text-[0.6rem] tracking-[0.2em] uppercase px-5 sm:px-6 py-2.5 transition-all duration-300 ${
                    activeCategory === cat
                      ? 'bg-[#c9a96e] text-black'
                      : 'border border-[#24201a] text-[#aaa199] hover:border-[#c9a96e30] hover:text-[#c9a96e]'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>
        </motion.div>
      </div>

      <div className="px-3 md:px-4">
        <div className="hidden md:flex gap-3">
          {[0, 1, 2].map((n, ci) => (
            <div key={n} className={`flex-1 flex flex-col ${
              ci === 1 ? 'mt-16' : ci === 2 ? 'mt-32' : ''
            }`}>
              <AnimatePresence>
                {col(n).map((item) => (
                  <PortCard
                    key={item.id}
                    item={item}
                    onClick={() => setLightboxIndex(filtered.findIndex(f => f.id === item.id))}
                  />
                ))}
              </AnimatePresence>
            </div>
          ))}
        </div>

        <div className="md:hidden flex gap-2">
          {[0, 1].map((n, ci) => (
            <div key={n} className={`flex-1 flex flex-col ${ci === 1 ? 'mt-10' : ''}`}>
              <AnimatePresence>
                {col2(n).map((item) => (
                  <motion.div
                    key={item.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.4 }}
                    className="relative overflow-hidden cursor-pointer mb-2 group"
                    onClick={() => setLightboxIndex(filtered.findIndex(f => f.id === item.id))}
                  >
                    <img
                      src={item.src}
                      alt={item.title}
                      loading="lazy"
                      className={`w-full object-cover transition-transform duration-700 group-hover:scale-105 ${
                        item.tall ? 'h-56' : 'h-36'
                      }`}
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-400" />
                  </motion.div>
                ))}
              </AnimatePresence>
            </div>
          ))}
        </div>
      </div>

      <div className="h-24 bg-gradient-to-b from-transparent to-[#060606] pointer-events-none" />

      <AnimatePresence>
        {lightboxItem && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.25 }}
            className="fixed inset-0 z-[9999] bg-[#050505]/98 flex items-center justify-center"
            onClick={() => setLightboxIndex(null)}
          >
            <button
              onClick={() => setLightboxIndex(null)}
              className="absolute top-6 right-6 w-10 h-10 flex items-center justify-center border border-[#1a1a1a] text-[#c9a96e60] hover:text-[#c9a96e] hover:border-[#c9a96e30] transition-all"
              aria-label="Close image preview"
            >
              <X size={14} />
            </button>
            <button
              onClick={prev}
              className="absolute left-4 md:left-8 w-10 h-10 flex items-center justify-center border border-[#1a1a1a] text-[#c9a96e60] hover:text-[#c9a96e] hover:border-[#c9a96e30] transition-all"
              aria-label="Previous image"
            >
              <ArrowLeft size={14} />
            </button>
            <button
              onClick={next}
              className="absolute right-4 md:right-8 w-10 h-10 flex items-center justify-center border border-[#1a1a1a] text-[#c9a96e60] hover:text-[#c9a96e] hover:border-[#c9a96e30] transition-all"
              aria-label="Next image"
            >
              <ArrowRight size={14} />
            </button>

            <motion.div
              key={lightboxItem.id}
              initial={{ opacity: 0, scale: 0.97 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
              className="flex flex-col items-center w-full px-16 md:px-28 max-w-5xl"
              onClick={e => e.stopPropagation()}
            >
              <img
                src={lightboxItem.src}
                alt={lightboxItem.title}
                className="max-h-[80vh] w-full object-contain"
              />
              <div className="flex items-center justify-between w-full mt-5">
                <div>
                  <p className="font-sans text-[0.48rem] tracking-[0.5em] text-[#c9a96e60] uppercase mb-1">{lightboxItem.category}</p>
                  <h3 className="font-serif text-xl text-[#f0ebe0]">{lightboxItem.title}</h3>
                </div>
                <p className="font-sans text-[0.52rem] tracking-[0.3em] text-[#666666] uppercase">
                  {lightboxIndex !== null ? lightboxIndex + 1 : ''} / {filtered.length}
                </p>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </section>
  );
}

function PortCard({ item, onClick }: { item: Item; onClick: () => void }) {
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.5 }}
      className="relative overflow-hidden cursor-pointer group mb-3"
      onClick={onClick}
    >
      <img
        src={item.src}
        alt={item.title}
        loading="lazy"
        decoding="async"
        className={`w-full object-cover transition-transform duration-[900ms] ease-out group-hover:scale-[1.06] ${
          item.tall ? 'h-[500px] lg:h-[580px]' : 'h-[300px] lg:h-[340px]'
        }`}
      />
      <div className="absolute inset-0 bg-gradient-to-t from-black/75 via-black/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-600" />
      <div
        className="absolute top-0 left-0 w-0 h-px bg-[#c9a96e] group-hover:w-10 transition-all duration-500 ease-out"
        style={{ transitionDelay: '50ms' }}
      />
      <div
        className="absolute top-0 left-0 w-px h-0 bg-[#c9a96e] group-hover:h-10 transition-all duration-500 ease-out"
        style={{ transitionDelay: '50ms' }}
      />
      <div className="absolute bottom-0 inset-x-0 p-5 translate-y-2 opacity-0 group-hover:translate-y-0 group-hover:opacity-100 transition-all duration-500">
        <p className="font-sans text-[0.48rem] tracking-[0.45em] text-[#c9a96e] uppercase mb-1.5">{item.category}</p>
        <h3 className="font-serif text-lg text-white leading-tight">{item.title}</h3>
      </div>
    </motion.div>
  );
}
