import { motion } from 'framer-motion';
import { categories } from '../data/galleryData';

const images = categories
  .filter(c => c.id !== 'reels')
  .flatMap(c => c.items.slice(0, 4).map(i => i.src));

const doubled = [...images, ...images];

export default function VisualStrip() {
  return (
    <section className="relative overflow-hidden bg-[#0a0806] h-48 md:h-64">
      <div className="absolute inset-0 bg-gradient-to-r from-[#0a0806] via-transparent to-[#0a0806] z-10 pointer-events-none" />
      <motion.div
        className="flex gap-4 h-full items-center"
        animate={{ x: ['0%', '-50%'] }}
        transition={{ repeat: Infinity, duration: 90, ease: 'linear' }}
      >
        {doubled.map((src, i) => (
          <div
            key={`${src}-${i}`}
            className="relative h-4/5 aspect-[4/5] shrink-0 overflow-hidden rounded-sm bg-[#0c0a08]"
          >
            <img
              src={src}
              alt=""
              loading="lazy"
              className="w-full h-full object-cover"
            />
          </div>
        ))}
      </motion.div>
    </section>
  );
}
