import { categories } from '../data/galleryData';

const images = categories
  .filter(c => c.id !== 'reels')
  .flatMap(c => c.items.slice(0, 4).map(i => i.src));

const doubled = [...images, ...images, ...images, ...images];

export default function VisualStrip() {
  return (
    <section className="relative overflow-hidden bg-[#0a0806] h-48 md:h-64">
      <div className="absolute inset-0 bg-gradient-to-r from-[#0a0806] via-transparent to-[#0a0806] z-10 pointer-events-none" />
      <div
        className="flex gap-4 h-full items-center"
        style={{ animation: 'stripScroll 80s linear infinite' }}
      >
        {doubled.map((src, i) => (
          <div
            key={`${src}-${i}`}
            className="relative h-4/5 aspect-[4/5] shrink-0 overflow-hidden rounded-sm bg-[#0c0a08]"
          >
            <img
              src={src}
              alt=""
              loading={i < 8 ? 'eager' : 'lazy'}
              className="w-full h-full object-cover"
            />
          </div>
        ))}
      </div>
      <style>{`@keyframes stripScroll { from { transform: translateX(0); } to { transform: translateX(-25%); } }`}</style>
    </section>
  );
}
