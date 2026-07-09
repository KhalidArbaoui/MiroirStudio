import { motion } from 'framer-motion';

const brands = [
  { name: 'Brand 1', logo: './images/logo639014082708108345.webp' },
  { name: 'Brand 2', logo: './images/images.jpg' },
  { name: 'Brand 3', logo: './images/images-1.jpg' },
  { name: 'Brand 4', logo: './images/images-2.jpg' },
];

export default function Brands() {
  return (
    <section className="section-pad bg-[#060606] relative">
      <div className="absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-[#c9a96e]/8 to-transparent" />
      <div className="container-site">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.9 }}
          className="mb-16 text-center"
        >
          <div className="flex items-center justify-center gap-4 mb-5">
            <div className="w-6 h-px bg-[#c9a96e]" />
            <span className="font-sans text-[0.52rem] tracking-[0.55em] text-[#c9a96e] uppercase font-medium">Partners</span>
            <div className="w-6 h-px bg-[#c9a96e]" />
          </div>
          <h2 className="font-sans text-5xl md:text-6xl text-[#f0ebe0] font-bold">Brands we work with</h2>
          <div className="gold-rule mt-8" />
        </motion.div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-8 md:gap-12 items-center">
          {brands.map((b, i) => (
            <motion.div
              key={b.name}
              initial={{ opacity: 0, y: 22 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.8, delay: i * 0.1 }}
              className="flex items-center justify-center p-6 md:p-8 group bg-white/[0.03] border border-white/[0.04] hover:border-[#c9a96e]/10 rounded-sm transition-colors duration-500"
            >
              <img
                src={b.logo}
                alt={b.name}
                className="max-h-14 md:max-h-20 w-auto object-contain transition-all duration-500 ease-out"
                style={{
                  filter: 'grayscale(1)',
                }}
                onError={(e) => { e.currentTarget.style.display = 'none' }}
              />
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
