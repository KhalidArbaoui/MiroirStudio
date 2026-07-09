import { motion } from 'framer-motion';

const testimonials = [
  {
    text: "MIROIR transformed how our café is perceived online. The photos are stunning — our Instagram engagement doubled within a week of posting.",
    author: "Fatima Al Mansoori",
    role: "Owner, Qasr Café — Abu Dhabi",
  },
  {
    text: "The team understood our oud brand's essence perfectly. Every shot tells a story of luxury and heritage. Exceptional quality.",
    author: "Khalid Al Rashidi",
    role: "Founder, Al Rashidi Fragrances — Dubai",
  },
  {
    text: "Professional, creative, and incredibly detail-oriented. Our jewelry collection has never looked so exquisite. Highly recommend.",
    author: "Noor Bint Saeed",
    role: "Creative Director, Lumière Jewels — Dubai",
  },
];

export default function Testimonials() {
  return (
    <section className="section-pad bg-[#080604] relative">
      <div className="absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-[#d4a84b]/8 to-transparent" />
      <div className="container-site">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.9 }}
          className="mb-16 text-center"
        >
          <div className="flex items-center justify-center gap-4 mb-5">
            <div className="w-6 h-px bg-[#d4a84b]" />
            <span className="font-sans text-[0.52rem] tracking-[0.55em] text-[#d4a84b] uppercase">Testimonials</span>
            <div className="w-6 h-px bg-[#d4a84b]" />
          </div>
          <h2 className="font-serif text-5xl md:text-6xl text-[#f0ebe0] italic">What clients say</h2>
          <div className="gold-rule mt-8" />
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {testimonials.map((t, i) => (
            <motion.div
              key={t.author}
              initial={{ opacity: 0, y: 22 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.8, delay: i * 0.1 }}
              className="flex flex-col p-8 lg:p-9 border border-white/[0.04] hover:border-[#d4a84b]/10 transition-colors duration-500"
              style={{ background: 'rgba(255,255,255,0.01)', boxShadow: '0 8px 40px rgba(0,0,0,0.3), inset 0 1px 0 rgba(255,255,255,0.015)' }}
            >
              <div className="font-serif text-6xl leading-none text-[#d4a84b]/10 mb-5 select-none">&ldquo;</div>
              <p className="font-sans text-[0.82rem] text-white/44 leading-[1.9] flex-1 italic">{t.text}</p>
              <div className="mt-9 pt-7 border-t border-white/[0.04]">
                <p className="font-serif text-[1.08rem] text-[#d4a84b]/78">{t.author}</p>
                <p className="eyebrow text-white/30 text-[0.48rem] mt-1.5">{t.role}</p>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
