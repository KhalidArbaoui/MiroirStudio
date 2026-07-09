import { useState, useCallback } from 'react';
import Navbar from './components/Navbar';
import Hero from './components/Hero';
import Portfolio from './components/Portfolio';
import VisualStrip from './components/VisualStrip';
import Services from './components/Services';
import About from './components/About';
import Brands from './components/Brands';
import Contact from './components/Contact';
import Footer from './components/Footer';
import GalleryPage from './components/GalleryPage';
import { categories } from './data/galleryData';

function scrollToSection(id: string) {
  const el = document.getElementById(id);
  if (!el) return;
  const top = Math.max(0, el.getBoundingClientRect().top + window.scrollY - 72);
  window.scrollTo({ top, behavior: 'smooth' });
}

export default function App() {
  const [page, setPage] = useState<'home' | 'gallery'>('home');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);

  const navigateToGallery = useCallback((categoryId: string) => {
    setSelectedCategory(categoryId);
    setPage('gallery');
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, []);

  const navigateToHome = useCallback(() => {
    setSelectedCategory(null);
    setPage('home');
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, []);

  const handleNavClick = useCallback((target: string) => {
    if (target === 'home') {
      navigateToHome();
      setTimeout(() => window.scrollTo({ top: 0, behavior: 'smooth' }), 50);
      return;
    }
    const sectionIds = ['portfolio', 'services', 'about', 'contact'];
    if (sectionIds.includes(target)) {
      if (page === 'gallery') {
        navigateToHome();
        setTimeout(() => scrollToSection(target), 400);
      } else {
        scrollToSection(target);
      }
      return;
    }
    const cat = categories.find(c => c.id === target);
    if (cat) navigateToGallery(target);
  }, [navigateToHome, navigateToGallery, page]);

  const category = selectedCategory
    ? categories.find(c => c.id === selectedCategory) || null
    : null;

  if (page === 'gallery' && category) {
    return (
      <div className="bg-[#0a0806] min-h-screen">
        <Navbar onNavClick={handleNavClick} currentPage={page} selectedCategory={selectedCategory} />
        <GalleryPage category={category} onBack={navigateToHome} />
        <Footer />
      </div>
    );
  }

  return (
    <div className="bg-[#0a0806] min-h-screen">
      <Navbar onNavClick={handleNavClick} currentPage={page} />
      <Hero />
      <Portfolio onSelectCategory={navigateToGallery} />
      <VisualStrip />
      <Services />
      <About />
      <Brands />
      <Contact />
      <Footer />
    </div>
  );
}
