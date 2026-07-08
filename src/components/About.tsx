import { motion } from 'framer-motion';

const stats = [
  { value: '50+',  label: 'Brands Served' },
  { value: '200+', label: 'Projects Done' },
  { value: '2',    label: 'Cities · UAE' },
];

const values = ['Precision', 'Storytelling', 'Elegance', 'Excellence'];

export default function About() {
  return (
    <section id="about" className="bg-[#060606] overflow-hidden">
      <motion.div
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 1.2 }}
        className="relative h-[55vh] md:h-[68vh] overflow-hidden"
      >
        <img
          src="/images/about-bg.jpg"
          alt="MIROIR Studio"
          loading="lazy"
          className="w-full h-full object-cover object-center"
          style={{ transform: 'scale(1.04)', transition: 'transform 2s ease-out' }}
          onLoad={e => { (e.target as HTMLImageElement).style.transform = 'scale(1.0)'; }}
        />
        <div className="absolute inset-0 bg-gradient-to-b from-[#060606]/20 via-transparent to-[#060606]" />
        <div className="absolute inset-0 bg-gradient-to-r from-[#060606]/50 via-transparent to-transparent" />

        <div className="absolute bottom-0 inset-x-0 container-site pb-12">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.9, delay: 0.3 }}
            className="text-center"
          >
            <div className="flex items-center justify-center gap-4 mb-4">
              <div className="w-6 h-px bg-[#c9a96e60]" />
              <span className="font-sans text-[0.52rem] tracking-[0.55em] text-[#c9a96e] uppercase">Our Story</span>
              <div className="w-6 h-px bg-[#c9a96e60]" />
            </div>
            <h2 className="font-serif text-5xl md:text-6xl lg:text-7xl text-[#f0ebe0] italic leading-[1.05]">
              Where vision<br />
              <span className="text-[#c9a96e]">meets craft</span>
            </h2>
          </motion.div>
        </div>

        <motion.div
          initial={{ opacity: 0, x: 16 }}
          whileInView={{ opacity: 1, x: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.9, delay: 0.5 }}
          className="absolute top-10 right-8 md:right-16 flex gap-10 md:gap-14"
        >
          {stats.map(s => (
            <div key={s.label} className="text-right">
              <p className="font-serif text-3xl md:text-4xl text-[#c9a96e]/80 leading-none">{s.value}</p>
              <p className="eyebrow text-white/32 text-[0.48rem] mt-1.5">{s.label}</p>
            </div>
          ))}
        </motion.div>
      </motion.div>

      <div className="container-site py-20 md:py-28">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 xl:gap-28">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.8 }}
            className="space-y-5"
          >
            <p className="font-sans text-[0.84rem] text-white/44 leading-[1.9]">
              MIROIR Studio is a creative product photography and videography studio based in the UAE, serving brands in Abu Dhabi and Dubai. We create clean, premium visuals designed to present products with clarity, style, and impact.
            </p>
            <p className="font-sans text-[0.84rem] text-white/44 leading-[1.9]">
              We work with cafés, restaurants, perfume and oud brands, cosmetics, jewelry, and other businesses that want their products to stand out through high-quality imagery and video content.
            </p>
            <p className="font-sans text-[0.84rem] text-white/44 leading-[1.9]">
              At MIROIR Studio, we believe strong visuals do more than showcase a product — they build trust, attract attention, and elevate the brand experience.
            </p>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.8, delay: 0.12 }}
          >
            <div className="grid grid-cols-2 gap-x-8 gap-y-5 mb-14">
              {values.map(v => (
                <div key={v} className="flex items-center gap-3">
                  <div className="w-3 h-px bg-[#c9a96e]/25" />
                  <span className="eyebrow text-white/34 text-[0.55rem]">{v}</span>
                </div>
              ))}
            </div>
            <div className="gold-rule mb-10" />
            <p className="font-serif text-2xl md:text-3xl text-white/68 italic leading-snug">
              Your brand deserves visuals<br />that stop the scroll.
            </p>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
