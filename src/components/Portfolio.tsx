import { motion } from 'framer-motion';
import { Play } from 'lucide-react';
import { categories, type Category } from '../data/galleryData';
import { openWhatsApp, waLink } from '../lib/whatsapp';

const WA_MSG = "Hello MIROIR Studio, I'm interested in your services.";

const isReels = (cat: Category) => cat.id === 'reels';

function CategoryCard({ category, index, onClick }: { category: Category; index: number; onClick: (id: string) => void }) {
  const isVideo = isReels(category);

  return (
    <motion.button
      initial={{ opacity: 0, y: 30 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.7, delay: index * 0.1, ease: [0.22, 1, 0.36, 1] }}
      onClick={() => onClick(category.id)}
      className="group w-full cursor-pointer border-0 p-0 text-left focus:outline-none bg-[#0c0a08]"
    >
      <div className="relative w-full overflow-hidden aspect-[4/5]">
        <img
          src={category.cover}
          alt={category.title}
          loading={index < 3 ? 'eager' : 'lazy'}
          className="w-full h-full object-cover transition-all duration-700 group-hover:scale-105"
        />

        {isVideo && (
          <>
            <div className="absolute top-0 inset-x-0 h-[15%] bg-gradient-to-b from-[#0a0806]/70 to-transparent pointer-events-none" />
            <div className="absolute bottom-0 inset-x-0 h-[15%] bg-gradient-to-t from-[#0a0806]/70 to-transparent pointer-events-none" />
            <div className="absolute inset-0 bg-gradient-to-br from-[#1a1a2e]/20 via-transparent to-[#0c0a08]/30 mix-blend-overlay pointer-events-none" />
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-full bg-[#d4a84b]/90 flex items-center justify-center transition-transform duration-500 group-hover:scale-110 shadow-2xl shadow-[#d4a84b]/30">
                <Play size={28} className="text-black ml-1 fill-black" />
              </div>
            </div>
            <div className="absolute top-4 right-4 pointer-events-none">
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-black/60 backdrop-blur-sm text-white/80 text-[0.4rem] tracking-[0.2em] uppercase font-nav font-medium border border-white/10">
                <svg viewBox="0 0 24 24" fill="currentColor" className="w-2.5 h-2.5">
                  <path d="M3 3h18v2H3V3zm0 4h18v2H3V7zm0 4h18v2H3v-2zm0 4h18v2H3v-2zm0 4h18v2H3v-2z" />
                </svg>
                Reel
              </span>
            </div>
            <div className="absolute bottom-3 inset-x-0 flex justify-center gap-1 pointer-events-none">
              {[...Array(6)].map((_, j) => (
                <div key={j} className="w-1 h-1 rounded-full bg-white/20" />
              ))}
            </div>
          </>
        )}

        <div className="absolute inset-0 bg-gradient-to-t from-[#0a0806]/90 via-[#0a0806]/10 to-transparent" />
        <div className="absolute inset-0 shadow-[inset_0_0_0_0_rgba(212,168,75,0)] group-hover:shadow-[inset_0_0_0_1px_rgba(212,168,75,0.25)] transition-all duration-500" />
      </div>

      <div className="p-4 sm:p-5 text-left">
        <h3 className="font-sans text-lg sm:text-xl text-white group-hover:text-[#d4a84b] transition-colors duration-300 leading-tight font-semibold">
          {category.title}
        </h3>
        {isVideo ? (
          <span className="inline-flex items-center gap-1.5 mt-2 font-sans text-[0.45rem] tracking-[0.32em] uppercase text-[#d4a84b]/60 font-medium">
            <Play size={9} className="fill-[#d4a84b]/60" />
            Watch Reels &rarr;
          </span>
        ) : (
          <span className="inline-block mt-2 font-sans text-[0.45rem] tracking-[0.32em] uppercase text-[#d4a84b]/60 font-medium">
            View Gallery &rarr;
          </span>
        )}
      </div>
    </motion.button>
  );
}

export default function Portfolio({ onSelectCategory }: { onSelectCategory: (id: string) => void }) {
  return (
    <section id="portfolio" className="bg-[#0a0806] section-pad">
      <div className="container-site">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8 }}
          className="mb-16 text-center"
        >
          <div className="flex items-center justify-center gap-4 mb-6">
            <div className="w-6 h-px bg-[#d4a84b]" />
            <span className="font-sans text-[0.52rem] tracking-[0.55em] text-[#d4a84b] uppercase font-medium">Our Work</span>
            <div className="w-6 h-px bg-[#d4a84b]" />
          </div>
          <h2 className="font-sans text-5xl md:text-6xl text-[#f0ebe0] font-bold">Browse by Category</h2>
          <div className="gold-rule mt-8" />
        </motion.div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 sm:gap-6 lg:gap-7">
          {categories.map((cat, i) => (
            <CategoryCard key={cat.id} category={cat} index={i} onClick={onSelectCategory} />
          ))}
        </div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8, delay: 0.4 }}
          className="mt-16 text-center"
        >
          <p className="font-sans text-[0.6rem] tracking-[0.25em] text-white/30 uppercase mb-7 font-medium">
            Want to discuss a project?
          </p>
          <a
            href={waLink(WA_MSG)}
            onClick={(e) => { e.preventDefault(); openWhatsApp(WA_MSG); }}
            className="wa-pulse inline-block bg-[#d4a84b] text-black font-sans text-[0.62rem] tracking-[0.24em] uppercase px-10 sm:px-12 py-4 hover:bg-[#d4b47a] transition-all duration-300 font-medium"
          >
            Get in Touch
          </a>
        </motion.div>
      </div>
    </section>
  );
}
